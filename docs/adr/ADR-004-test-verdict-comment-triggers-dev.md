# ADR-004: Test Verdict Comment Is the Only Trigger for Dev Fixes

**Date**: 2026-11-07
**Status**: Accepted
**Deciders**: Brian Campbell

---

## Context

Test's Phase 3 instructions told it to finish final verification with `gh pr review --approve` or `--request-changes`. That can never work: Dev and Test share the bot account (`briankcampbell-streamvault-bot`), the bot opens every feature PR, and GitHub does not let a PR author review their own PR. The same persona file already said so in its Phase 4 section.

Dev was woken for fixes by `trigger-dev-on-test-commit.yml`, which required two things together: a push by Test to a test directory, and a formal `changes_requested` review on the PR. Only Brian can produce that review. So:

- **When Test found problems in final review**, it could only post a comment. No formal review existed, Dev was never woken, and the story stalled with every run green.
- **When Test found failures in existing tests**, it had nothing new to push, so even the push half of the trigger never fired.
- **Once Brian had ever requested changes**, his review state stayed on the PR, so any later Test push woke Dev, whether or not Test meant to send the PR back.

ADR-003's outcome verification could not catch the first case: Test did post a comment, which is what the check looked for.

---

## Decision

### 1. Test's verdict is a PR comment with a machine-readable marker

Every Phase 3 run (final verification and each re-verification) and every Phase 4 run (after Brian's Changes Requested review) ends with exactly one PR comment whose first line is one of:

```
<!-- test-verdict: APPROVED -->
<!-- test-verdict: CHANGES_REQUESTED -->
```

It is an HTML comment, so it does not render. Workflows read the marker, never the prose. Test no longer runs `gh pr review`.

### 2. The verdict comment is Test's last action

Test pushes any new regression or failing tests first and posts the verdict comment last. Dev is woken by the comment, so the tests Dev must make pass are always on the branch before Dev starts.

### 3. One trigger wakes Dev for fixes

`trigger-dev-on-test-verdict.yml` replaces `trigger-dev-on-test-commit.yml`. It fires on a new PR comment by the bot carrying the `CHANGES_REQUESTED` marker, on an open `feature/` PR. It is the only automated path that wakes Dev to fix an open PR, so one verdict can never wake Dev twice.

`issue_comment` workflows always run from the default branch, so the new trigger applies to every open PR as soon as it is on `main`, including PRs whose branches predate this change.

### 4. Brian's review uses the same path

`trigger-on-changes-requested.yml` still wakes Test when Brian requests changes. Test writes failing tests for each of Brian's points, pushes them, and posts a `CHANGES_REQUESTED` verdict comment. That comment, not the push and not Brian's review, wakes Dev.

### 5. Fix rounds are capped and the cap resets on Brian's review

When the bot has posted more than 3 `CHANGES_REQUESTED` verdicts on a PR since Brian's most recent review (of any kind), the next one escalates to Brian through `scripts/escalate-to-brian.sh` instead of waking Dev. This mirrors the 3-round cap on design iteration and stops Test and Dev from cycling indefinitely. The count resets whenever Brian submits a review, so Brian's own Changes Requested round never escalates straight back to him.

### 6. Outcome verification checks for the verdict

Under ADR-003, the Test final-review and re-verification launches must produce a verdict comment (any verdict). The Phase 4 launch must produce both a new Test commit and a `CHANGES_REQUESTED` verdict comment. A plain comment no longer passes.

---

## Consequences

- Test's own findings now reach Dev automatically. Previously they stalled without any signal.
- The hand-off to Brian is explicit: an `APPROVED` verdict comment means the PR is ready for his review. A `CHANGES_REQUESTED` verdict means Dev is already working and Brian should wait.
- `trigger-dev-on-test-commit.yml` is deleted. Pushes by Test no longer wake Dev on their own.
- The marker string is a contract between `.claude/personas/test/CLAUDE.md`, `trigger-dev-on-test-verdict.yml`, and `scripts/persona-outcome-check.sh`. Changing it means changing all three.
- Anyone who can comment as the bot can wake Dev by posting the marker. Only the bot token's holders (the personas and the workflows) can do that.
- Brian's step-by-step review instructions for both outcomes (Approve, Request changes) live in `ai-dev-toolkit/docs/agentic-workflow-diagram.md`, next to the diagram they act on.

---

## Alternatives Considered

**Wake Dev on Test's verdict comment AND on Test's pushes (keep `trigger-dev-on-test-commit.yml`).** Rejected. After Brian has requested changes once, his review state stays on the PR, so a single Test verdict would wake Dev twice: once for the push, once for the comment.

**Escalate every Test CHANGES_REQUESTED verdict to Brian and let him route it.** Rejected by Brian. It makes him a relay for findings that Dev can act on directly, and the fix-round cap already escalates when Test and Dev stop converging.

**Have Test use a second GitHub account so it can submit formal reviews.** Rejected. It adds an account and a token to manage, and still needs a separate trigger for failures that come with no new commit.

**Parse the prose "Recommendation: CHANGES REQUESTED" line instead of a marker.** Rejected. Prose drifts with wording and formatting, and a marker is unambiguous to both the persona and the workflow.