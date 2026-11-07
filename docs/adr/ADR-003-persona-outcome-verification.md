# ADR-003: Persona Outcome Verification and Escalation

**Date**: 2026-11-07
**Status**: Accepted
**Deciders**: Brian Campbell

---

## Context

On 2026-11-06, `trigger-dev-implement.yml` launched the Dev persona for STORY-009. Dev found that `LibraryServiceTest.java` still called a 2-argument `LibraryService` constructor, while the agreed contract (revised in design round 2) required 3 arguments. Correctly, Dev did not edit Test's file and did not add a compatibility constructor. It stopped and asked Brian how to proceed.

Personas run headless (`claude -p` via `streamvault-*-auto.sh`), so the question went only to the Actions log. The persona exited 0, the workflow reported success, and STORY-009 sat stalled for about 23 hours with a green checkmark and no notification.

Two separate gaps produced this:

1. **No outcome verification.** Every trigger workflow treated "the persona process exited 0" as "the work was done." A persona that asks a question, gives up, or crashes in a way that still exits 0 is indistinguishable from success.
2. **No reconciliation of test code during design iteration.** Test's Phase 2 instructions covered revising the test plan and contracts but never required updating already-committed test code to match. Dev's design review read only the design documents, never the test code, so three review rounds missed the mismatch.

---

## Decision

### 1. Every persona launch verifies its expected outcome

Each step that launches a persona container is followed by a verification step that checks repo state on GitHub through `scripts/persona-outcome-check.sh`, never the persona's exit code. Every check that can be satisfied by an earlier run also requires that the persona pushed new commits during this run (`branch-advanced`).

| Workflow | Expected outcome |
|---|---|
| `trigger-test-next-story.yml` | `feature/story-NNN-*` branch created (or new Test commits, if it already existed) |
| `trigger-dev-review.yml` | New Dev commit, and `agreed.md` or `dev-feedback-rN.md` on the branch |
| `trigger-test-revision.yml` | New Test commit, and `test-revision-rN.md` on the branch |
| `trigger-dev-implement.yml` | New Dev commit, and an open PR for the branch |
| `trigger-dev-on-test-verdict.yml` | New Dev commit on the branch (see ADR-004) |
| `trigger-test-on-dev-fix.yml` | New Test verdict comment on the PR (see ADR-004) |
| `trigger-test-final-review.yml` | New Test verdict comment on the PR (see ADR-004) |
| `trigger-on-changes-requested.yml` | New Test commit, and a `CHANGES_REQUESTED` verdict comment (see ADR-004) |
| `trigger-po-on-changes-requested.yml` | New PO commit on the specs branch |

The launch step has a 180-minute step timeout. A step timeout is a failure, which triggers escalation; the 6-hour job timeout is a cancellation, which would skip it.

### 2. A missing outcome escalates to Brian

When verification fails, `scripts/escalate-to-brian.sh` opens a `needs-brian` issue containing the stage, the reason, the workflow run link, and the tail of the persona's output (where any question it asked appears), then the run ends red.

- The issue is created by the bot account (`STREAMVAULT_BOT_GH_TOKEN`) and assigned to `birdman74`. GitHub never notifies a user about their own activity, so a token belonging to `birdman74` must not be used. Assignment makes Brian a participant, so he is notified and emailed (with participating email notifications on).
- One open issue per story and stage: a repeat escalation comments on the existing issue instead of opening a duplicate.
- Output that looks like a GitHub or Anthropic credential is redacted before posting. The same output is already visible in the public Actions log; the issue adds no new exposure.

The design-iteration-limit escalation in `trigger-dev-review.yml` and the fix-round-limit escalation in `trigger-dev-on-test-verdict.yml` (ADR-004) use the same script.

### 3. Test code is part of the design contract

- **Test (prevent):** every contract change in a design revision must be applied to every existing test that uses the changed element, in the same commit, and recorded in a required Test Code Reconciliation table in `story-NNN-test-revision-rN.md`.
- **Dev (detect):** design review includes a Test Code Conformance check of all committed test code against the current contracts. Dev may not agree while any mismatch exists, and `story-NNN-agreed.md` must include the conformance table.
- **Dev (stop):** if a mismatch still reaches implementation, Dev stops with a `NEEDS BRIAN:` message instead of editing Test's files or adding workarounds. Decision 2 turns that into an escalation.

---

## Consequences

- A stalled persona now produces a red run and an assigned issue within minutes, instead of a green run and silence.
- Any new workflow that launches a persona must add the record, verify, and escalate steps (see any `trigger-*.yml` for the pattern) and must define its expected outcome in terms of repo state on GitHub.
- Verification adds about 20 to 60 seconds per persona run (polling for GitHub to reflect the final push).
- Push-triggered runs use the workflow files on the pushed branch. Feature branches created before this change keep the old workflows until they merge; dispatched and PR-event runs use the new ones.
- Verification checks that an artifact exists, not that its content is good. Content quality remains the job of the reviewing persona (decision 3) and of Brian.

---

## Alternatives Considered

**Have personas report their own failures (e.g. exit non-zero or open an issue themselves).** Rejected as the primary mechanism. It depends on the persona recognizing and reporting its own failure, which is exactly what failed here: Dev exited normally after asking its question. Personas are still told to lead with `NEEDS BRIAN:` so their question is easy to find in the escalation issue, but detection does not depend on it.

**Detect "NEEDS BRIAN" text in persona output.** Rejected as the trigger. Text matching misses crashes, timeouts, and personas that phrase the question differently. Checking for the expected artifact catches every kind of stall.

**Fail the workflow run without opening an issue.** Rejected. Failed-run emails go to the user who triggered the run, which for persona pushes may be the bot, so the notification is not reliable.