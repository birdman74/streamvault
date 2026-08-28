# Test Revision — STORY-002 Round 1

Responds to `story-002-dev-feedback-r1.md`. Both concerns are accepted as-is. No AC coverage was
dropped; one AC (AC-6) is strengthened and a new failure path is added under AC-5.

## Concern 1: `emailVerified` never consulted — accepted

Agreed this is a real trust-boundary gap. `story-002-api-contracts.md`'s resolution order now
inserts a step immediately after `verify()` and before any repository lookup: if
`GoogleUserInfo.emailVerified()` is `false`, throw `GoogleSignInException` and touch no account.

**Answer to Dev's question 1**: reuse `GoogleSignInException`, same 401 message, not a distinct
exception type. No AC requires the failure to be distinguishable at the API layer, and both cases
(verification failure, unverified email) are the same category of thing from the caller's
perspective — a Google-side trust failure. If distinguishability in logs turns out to matter later,
that's a logging concern inside the implementation, not a contract/exception-type concern.

Two new failing tests added to `GoogleAuthServiceTest`:
- `should_throwGoogleSignInException_when_emailNotVerified`
- `should_neverCallUserRepository_when_emailNotVerified`

Test plan updated: this pair is mapped primarily to AC-5 (it's a sign-in failure path that must
show a clear error and create no account) and noted as strengthening AC-6 (the collision guard's
email-keyed guarantee is only sound if Google actually verified that email).

## Concern 2: `@Transactional` wraps the outbound Google call — accepted

Agreed there's no atomicity benefit and a real connection-pool risk. Dropped `@Transactional` from
`GoogleAuthService.googleSignIn()` entirely (not narrowed) — the DB work is a single
`findByGoogleId`/`findByEmail` read followed by at most one `save()`, already atomic as one
statement, so there's no multi-statement interleaving to protect.

**Answer to Dev's question 2**: no objection, and no narrower scope is needed either. There was no
specific multi-statement interleaving in mind; the transaction boundary in the original contract was
over-cautious, not deliberate. `story-002-api-contracts.md` now states explicitly why AC-5's
"no partial account on failure" guarantee still holds without it: both failure paths return before
any write is attempted.

No test changes were needed for this concern — no test asserts on transaction boundaries.

## Files Changed

- `docs/specs/design/story-002-api-contracts.md` — resolution order gains the `emailVerified` check
  as new step 2; `@Transactional` removed from the `googleSignIn()` signature and step list;
  `GoogleSignInException` doc comment updated to note it's now also thrown directly by
  `GoogleAuthService`.
- `docs/specs/design/story-002-test-plan.md` — AC-5 and AC-6 rows updated to reference the two new
  tests.
- `src/test/java/com/streamvault/backend/auth/GoogleAuthServiceTest.java` — two new failing tests
  added (see above). Still fails to compile until Dev implements `GoogleAuthService`, consistent
  with the existing RED state.

## AC Coverage Check

All six ACs remain covered; none were narrowed. AC-5 gains a test, AC-6's existing tests are
unchanged but their guarantee is now backed by an upstream check.

## Status

Agreed from Test's side pending Dev's confirmation on both points. If Dev has no further concerns,
next step is Dev committing `story-002-agreed.md` to trigger implementation.
