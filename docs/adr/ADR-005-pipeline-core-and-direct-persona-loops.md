# ADR-005: Pipeline Core, Single Config, and Direct Persona Loops

**Date**: 2026-11-24
**Status**: Accepted
**Deciders**: Brian Campbell
**Amends**: ADR-002, ADR-003, ADR-004

---

## Context

A full evaluation of every persona hand-off (2026-11-08) found that the pipeline worked for the happy path but leaked in three ways.

**1. The logic was copied, not shared.** Ten `trigger-*.yml` workflows each carried about 120 lines of the same record, launch, verify and escalate steps, plus their own author checks, state checks, round counting, prompts and expected outcomes. A fix had to be made nine times, and several were not:

- Dev's pushes after it opened a PR woke Test a second time, next to the final review (seen on STORY-009: commit `aa3af00` after PR #31 opened).
- Design-loop state checks read the commit that triggered the run, not the live branch, so a run queued for hours behind another persona could relaunch a round that was already done. Round detection was hard-coded to `for i in 1 2 3`.
- A Changes Requested review on a specs PR also fired the story-PR workflow, which failed red with no escalation.
- A re-run or a second verdict comment could wake Dev for a verdict it had already fixed, then raise a false `needs-brian` issue.
- A Dev re-dispatched after its implementation was committed only needed to open the PR, but the outcome check demanded a new commit and raised a false `needs-brian` issue.
- The kickoff check passed on an empty branch; a closed-unmerged story PR paused the queue silently; `needs-brian` issues never closed themselves and did not say how to resume.

**2. Push-triggered runs used the feature branch's copy of everything.** GitHub runs a push workflow from the pushed commit, so a fix merged to `main` did not reach an in-flight story until `main` was merged into its branch, while comment-triggered workflows already ran from `main`.

**3. Personas routed their disagreements through Brian.** When Dev found a defective test during implementation, it stopped with `NEEDS BRIAN` (twice on STORY-009: a stale constructor call, then a strict-stubbing contradiction). When Test or Dev found a problem with an acceptance criterion, both persona files said to surface it to Brian. Brian's standing rule is that persona-to-persona back-and-forth is direct until a round threshold is exceeded.

Every threshold (round limits, timeouts, retry counts, wait times, account names) was also written inline in several places.

---

## Decision

### 1. One registry, one runner, thin adapters

| Piece | Role |
|---|---|
| `scripts/transition.sh` | The single registry of hand-offs. For each of 12 transitions it holds the routing rule, the live state check, round counting, the round limit, the prompt, the expected outcome, and the "how to resume" text. |
| `.github/workflows/persona-run.yml` | One reusable workflow: recheck the hand-off is still needed, record state, launch the persona with the configured timeout, remove its container, verify the outcome, escalate on failure, and resolve the story's open escalations on success. |
| `pipeline-*.yml` (6 files) | Event adapters. A gate job on a GitHub-hosted runner checks out `main`, runs `transition.sh route <event>`, and calls `persona-run.yml` at `@main` only when a persona is needed. |

The adapters are:

| Adapter | Event | Transitions |
|---|---|---|
| `pipeline-feature-push.yml` | push to `feature/story-*` | every design, implementation, feedback and PO loop hand-off, plus Test re-verification |
| `pipeline-pr-opened.yml` | story PR opened or reopened | `test-final-review` |
| `pipeline-test-verdict.yml` | CHANGES_REQUESTED verdict comment | `dev-fix` |
| `pipeline-review-submitted.yml` | Brian's Changes Requested review | `test-brian-review` (story PR), `po-spec-revision` (specs PR) |
| `pipeline-main-and-merge.yml` | story spec pushed to `main`, PR closed | `test-kickoff` (the queue) |
| `pipeline-manual.yml` | manual dispatch | any transition, skipping author, state and limit checks |

Because the gate checks out `main` and the persona job is `persona-run.yml@main`, everything except the thin event wiring runs from `main` for every story, whatever branch it is on.

### 2. One push, at most one hand-off

The feature-push gate routes a push by its head commit's persona and the hand-off file the push brought in, in a fixed priority order (PO answer, PO question, implementation feedback, feedback response, agreement, Dev feedback, Test plan or revision, any other Dev push). A push can never wake two personas, and a Dev push only wakes Test for re-verification when Test's latest verdict is CHANGES_REQUESTED and the push is newer than it. Test's first look at a PR always comes from the final review.

### 3. Live state, checked twice

The gate decides from the live branch, PR and comments on GitHub, never from the triggering commit's tree. `persona-run.yml` checks again just before launch, because the single self-hosted runner can hold a run for hours. A stale hand-off is skipped green.

### 4. Direct persona loops

| Loop | Asker writes | Answerer writes | Limit |
|---|---|---|---|
| Design review | Dev: `dev-feedback-rN.md` | Test: `test-revision-rN.md` | `DESIGN_ROUND_LIMIT` |
| Defective test (implementation and fix phases) | Dev: `impl-feedback-rN.md` | Test: `impl-feedback-response-rN.md` | `IMPL_FEEDBACK_ROUND_LIMIT` per story |
| Acceptance-criteria question (any phase) | Test or Dev: `po-question-rN.md` | PO: `po-answer-rN.md` | `PO_QUESTION_LIMIT` per story |
| PR fix | Test: CHANGES_REQUESTED verdict | Dev: fix commit | `DEV_FIX_ROUND_LIMIT` since Brian's last review |

All files live in `docs/specs/design/` on the story's feature branch. The answer's push wakes the asker, which resumes the phase it was in.

PO may amend the story's own acceptance criteria on the feature branch, so Brian reviews the change with the story PR, which is the usual practice when specs live in the repo. Anything beyond the story (new scope, another story, prerequisites) is not PO's to settle headless: PO stops with `NEEDS BRIAN`, which escalates. Test's verdict comment lists every spec change on the branch in a Spec Changes section, so none can reach Brian unnoticed.

`NEEDS BRIAN` remains only for decisions no persona may make.

### 5. One config file

`.github/pipeline-config.env` holds every knob: account logins and persona git authors, the full-suite verify command named in prompts, launcher paths, the four round limits, the persona timeout, outcome-check retries, the API propagation wait, and the project board names. `scripts/pipeline-config.sh` parses it (never sources it, so a value cannot run as code) and validates every key: unknown, missing or duplicate keys, non-numeric limits, invalid logins, relative paths, and a timeout above 330 minutes (which the 360-minute job limit would turn into a cancellation that skips escalation) all fail the run. Persona files refer to the limits by name instead of repeating numbers.

Job-level `if:` conditions cannot read a file, so they hold only account-free pre-filters (a review state, a verdict marker). Every account check happens in the gate, from the config.

### 6. Escalations close themselves and say how to resume

Every `needs-brian` issue has a "How to resume" row naming the `Pipeline - Manual Dispatch` transition and inputs. When a persona run for a story succeeds, every open `needs-brian` issue for that story is closed with a link to the run. A story PR closed without merging escalates instead of pausing the queue silently.

### 7. Smaller fixes made at the same time

- Persona launchers give each run its own container name and remove it on exit or interruption; `persona-run.yml` removes it again in an always-run step, so a timed-out persona can never block the next launch or collide with an interactive session.
- The launch step uses `shell: bash` (pipefail), so a launcher failure is no longer hidden by `tee`.
- All workflows use `actions/checkout@v5` (Node.js 24).
- The board sync reads the board, field and column names from the config.

---

## Amendments to earlier ADRs

- **ADR-002.** The author and state checks now live in `transition.sh`, run in the gate for every push. The pushed file list is computed by the gate itself: everything since `main` for a new branch, the first-parent diff for a force-push, and the range from the previous head otherwise. `paths:` filters are no longer used on feature branches.
- **ADR-003.** The expected outcome of every transition is defined in `transition.sh verify`. The table in ADR-003 is superseded by that function. New outcomes: a kickoff needs the test plan and contracts on the branch, not just a branch; any persona may satisfy its check by asking PO instead; a resumed Dev passes by opening the PR.
- **ADR-004.** The trigger is now `pipeline-test-verdict.yml` (transition `dev-fix`), and the limit is `DEV_FIX_ROUND_LIMIT`. Dev reports a defective test instead of stopping, and Test's Phase 4 verdict lists review points no test can express under Non-testable review points.

---

## Consequences

- Adding or changing a hand-off means editing `transition.sh` (and, for a new event, one small adapter). Every rule is covered by mock tests against a stateful `gh` mock.
- `persona-run.yml` is referenced as `birdman74/streamvault/.github/workflows/persona-run.yml@main`. A copy of this pipeline in another repository must change that line in each adapter.
- Feature branches created before this change still carry the old `trigger-*.yml` files until `main` is merged into them, and pushes to them run those old files. Merge `main` into every open feature branch when this lands.
- Brian is involved only for spec approval, PR review, and an escalation: a loop past its limit, a persona that stalled, a PO answer beyond the story's scope, or a story PR closed without merging.

---

## Alternatives Considered

**Keep per-workflow logic and fix each workflow.** Rejected. It fixes today's gaps but keeps nine copies to drift apart, and it does not solve feature branches running stale copies.

**Repository variables (`vars.*`) for account names.** Rejected. They work in job-level `if:` conditions, but they live outside git, so changes are not reviewed or versioned, and the knobs would be split between a file and the settings page.

**Route acceptance-criteria conflicts to Brian, or require a separate specs PR for any AC change.** Rejected. Both stop the story until Brian acts, against the standing rule that persona loops are direct until a limit is exceeded. Scope changes still go to Brian.

**A single dispatcher workflow for every event.** Rejected. GitHub needs a separate `on:` block per event type and each event carries different context; six small adapters keep each event's wiring readable.
