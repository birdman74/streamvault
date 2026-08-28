# Dev Feedback — STORY-002 Round 1

## Summary
Needs revision. The test plan's AC coverage mapping is thorough and the API contracts closely
mirror STORY-001's existing patterns (AuthResponse reuse, GlobalExceptionHandler style, DTO
redaction convention). Two concerns in `story-002-api-contracts.md` need resolution before I start
implementation: one is a security-relevant trust-boundary gap in the resolution algorithm, the
other is a resource-usage issue with the proposed transaction boundary.

## Concerns

### Concern 1: `emailVerified` is part of the contract but never consulted
**File**: story-002-api-contracts.md (GoogleAuthService section, "Resolution order")
**Issue**: `GoogleUserInfo` carries `emailVerified`, but the 4-step resolution algorithm never
reads it — both the AC-2 create-path and the AC-6 collision-guard trust the raw `email` string
from Google unconditionally. If a Google ID token ever surfaces `email_verified=false` (Google
does support this for some Workspace/hosted-domain configurations with unverified secondary
addresses), the current algorithm would happily bind a StreamVault account to an email the Google
account holder hasn't actually proven ownership of. Concretely: an attacker holding such a token
for `victim@example.com` could Google-sign-in and claim that email in StreamVault *before* the
real victim ever registers, so when the victim later tries `/api/auth/register` with their own
email/password, they'd hit "Email is already registered" and be locked out of their own address.
AC-6's collision guard is only as trustworthy as the email claim it's keyed on.
**Suggestion**: Check `emailVerified` immediately after `verify()`, before any repository lookup —
if false, throw `GoogleSignInException` (same 401 path as any other verification failure). If Test
intended the field to be purely informational, it should be dropped from the contract instead,
since carrying an unused trust signal through the seam is what created this gap.
**AC coverage**: Does not drop any AC. Strengthens AC-6, whose entire guarantee depends on the
email being trustworthy.

### Concern 2: `@Transactional` as specified wraps the outbound Google network call
**File**: story-002-api-contracts.md (GoogleAuthService section, step 4)
**Issue**: Step 4 puts "verify-then-resolve-then-save" inside a single transaction, and step 1
(`googleTokenVerifier.verify(...)`) is the real, non-mocked implementation's outbound HTTPS call
to Google. That means every Google sign-in would hold a checked-out Postgres connection for the
full round-trip latency to Google's servers, not just for the DB work. Given this project's
cost-constrained t3.micro Postgres (see root CLAUDE.md), that's a connection-pool exhaustion risk
under any concurrent sign-in load, and it buys no atomicity benefit — the actual DB work in this
method is one `findByGoogleId`/`findByEmail` read followed by at most one `save()`, which is
already atomic as a single statement. STORY-001's `AuthService` has no equivalent pattern to match
against.
**Suggestion**: Drop `@Transactional` from `googleSignIn()`, or if Test has a specific atomicity
concern in mind that I'm not seeing, scope it narrowly around the resolve/save portion only, after
`verify()` returns.
**AC coverage**: No AC references transaction boundaries. AC-5's "no partial account on failure"
guarantee is already satisfied by `save()` being a single atomic write with nothing to roll back on
the failure paths (both failure paths return before any write is attempted), so narrowing or
removing `@Transactional` does not weaken AC-5.

## Questions for Test
1. For Concern 1: should an `emailVerified=false` result use the same 401 message as other
   verification failures, or does this warrant a distinct error/exception so it's distinguishable
   in logs? I'd default to reusing `GoogleSignInException` unless you see a reason to split it out.
2. For Concern 2: any objection to dropping `@Transactional` outright, given the DB work reduces to
   a single `save()` call? If you had a specific interleaving in mind that needs atomicity across
   multiple statements, let me know and I'll implement to that instead.
