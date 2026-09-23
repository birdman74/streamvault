# ADR-002: Push-Triggered Workflow Guard Pattern

**Date**: 2026-09-23
**Status**: Accepted
**Deciders**: Brian Campbell

---

## Context

While reviewing STORY-007, Brian made a single commit to `STATUS.md` and separately performed an interactive rebase (reordering commits already on the branch, no code content changed) followed by a force-push. That force-push alone fired five workflows against the same commit SHA: `StreamVault CI`, `CodeQL`, `Trigger Test — Dev Pushed Fix to Open PR`, `Trigger Dev — Test Plan Ready for Review`, and `Trigger Dev — Agreed Design, Begin Implementation`.

The root cause: GitHub's `push` event `paths:` filter is unreliable across a force-push or history rewrite. It is intended to compare the files changed by the pushed commit(s) against the filter's glob patterns, but for a force-push the underlying diff calculation can span the entire rewritten commit range rather than just the resulting top commit. A push that only reorders existing commits, touching no file content at the final tree, can still cause a `paths:` filter to match files that appear anywhere in that range.

Two of the five workflows that fired had a guard beyond the `paths:`/`branches:` trigger: they checked the actual commit author (`git log -1 --format='%an'`) and a live state condition (an open PR exists, or a PR carries a specific review state) before launching a persona container, and correctly self-skipped. The other two workflows relied on the `paths:` filter alone and launched Dev persona containers with no real work to do. No incorrect commits, comments, or state changes resulted this time, only because the Dev persona reasoned its way to "nothing to do" from repo state, which is not a guarantee, it is a coincidence of what state the branch happened to be in.

`trigger-test-next-story.yml` has the same theoretical exposure on pushes to `main` (a `paths:` filter on `docs/specs/story-*.md`), though it was not observed to misfire in this incident; its downstream queue script already provides some protection since it independently checks issue state before acting.

---

## Decision

Every workflow that launches a Dev, Test, or PO persona container in response to a `push` event must verify two things inside the job itself, and must not treat the `on: push: paths:` filter as sufficient on its own:

1. **Commit authorship.** Confirm via `git log -1 --format='%an'` (or, where the precise file list of the triggering commit matters, `git diff-tree --no-commit-id --name-only -r <sha>`) that the action causing this push is actually attributable to the persona or actor whose work this workflow exists to react to. `github.actor` at the job `if:` level identifies who pushed, not what was actually pushed or by which persona's commit; it is not a substitute for this check.
2. **Current state.** Confirm the condition the workflow exists to react to is not already satisfied before launching a container: the expected downstream artifact does not already exist, no PR already covers the state in question, no issue label already reflects it, etc.

`paths:` and `branches:` filters on the `on:` trigger remain in place as a cheap pre-filter to avoid invoking the job at all on obviously unrelated pushes, but they are never trusted as the sole gate for whether to launch a persona or perform a mutating action.

---

## Consequences

- `trigger-dev-review.yml`, `trigger-dev-implement.yml`, and `trigger-test-revision.yml` were updated to add commit-author checks and a state check (does the expected downstream file already exist), matching the pattern below.
- `trigger-test-next-story.yml` was updated to add an explicit `git diff-tree` check confirming the triggering commit itself touched a `docs/specs/story-*.md` file, rather than trusting the `paths:` filter match.
- `trigger-test-on-dev-fix.yml` and `trigger-dev-on-test-commit.yml` already followed this pattern before the incident and required no changes; they are the reference implementation and are annotated as such.
- Any new push-triggered workflow that launches a persona container, or performs any other mutating GitHub action (label change, issue close, PR review), must include both checks before merge. This applies regardless of whether the `paths:` filter looks precise, since the filter's unreliability is on the GitHub side, not a matter of writing a tighter glob.
- A force-push or interactive rebase on a feature branch, or a force-push to `main`, is now expected to be a no-op across every trigger workflow in this repository, not just the ones that happened to already be guarded.
- `CONTRIBUTING.md` and this ADR should be consulted before adding any new workflow that reacts to a `push` event.

---

## Alternatives Considered

**Prohibit force-pushes to feature branches via branch protection.** Rejected. Reordering commits before merge (e.g. moving a pre-merge STATUS.md update after the last substantive commit) is a legitimate and useful part of Brian's own workflow. The bug is in trusting an unreliable GitHub trigger mechanism, not in the git operation that exposed it.

**Rely on `paths:` filters alone with tighter glob patterns.** Rejected. The failure mode is not glob imprecision, it is that GitHub's diff calculation for a force-push does not reliably reflect the actual file state of the resulting commit. A tighter glob narrows what can match but does not fix the underlying unreliability.

**Add a per-branch concurrency lock to prevent multiple workflows firing simultaneously.** Not adopted as a replacement. This would reduce the chance of overlapping container runs but does nothing to prevent a single workflow from launching a container with no real work to do. May be worth adding later as a secondary safeguard against concurrent runs, but it does not address the root cause and is not a substitute for the author/state checks above.