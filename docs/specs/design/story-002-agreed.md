# Agreed Design — STORY-002

## Summary
Test and Dev agree on the design after 1 round of review. Round 1 raised two concerns
(`story-002-dev-feedback-r1.md`); Test accepted both without pushback in
`story-002-test-revision-r1.md`. This round (2), Dev reviewed the revised
`story-002-api-contracts.md` and `story-002-test-plan.md` against the PO story's acceptance
criteria, the existing STORY-001 codebase (`User`, `UserRepository`, `AuthService`,
`GlobalExceptionHandler`, `SecurityConfig`, `AuthController`, `AuthResponse`), and the three
already-committed failing test files (`GoogleAuthServiceTest`, `AuthControllerGoogleTest`,
`GoogleSignInRequestValidationTest`). No further concerns found. Proceeding to implementation.

## Final API Contracts
As defined in `story-002-api-contracts.md`, with the round 1 amendments already folded in:
- `GoogleAuthService.googleSignIn()` checks `GoogleUserInfo.emailVerified()` immediately after
  `verify()` and before any repository lookup, throwing `GoogleSignInException` (401) if false.
- No `@Transactional` on `googleSignIn()` — the DB work is a single read followed by at most one
  `save()`, already atomic, and wrapping it would hold a Postgres connection across the outbound
  call to Google.
- `POST /api/auth/google` returns the existing `AuthResponse` shape (200), reuses the existing
  validation-error shape (400), and adds two new mapped exceptions: `GoogleSignInException` (401,
  fixed message) and `GoogleAccountEmailCollisionException` (409, fixed message per AC-6).

## Implementation Plan
1. Migration `V2__add_google_id_to_users.sql`: drop `NOT NULL` on `password_hash`, add unique
   nullable `google_id`.
2. Extend `User`: make `passwordHash` field/column nullable, add nullable `googleId` field +
   getter, add `static User googleUser(String email, String googleId)` factory. Leave the existing
   `User(String email, String passwordHash)` constructor untouched.
3. Add `UserRepository.findByGoogleId(String)`.
4. Add `GoogleSignInRequest` DTO (redacted `toString()`, matching `LoginRequest`/`RegisterRequest`
   pattern), `GoogleUserInfo` record, `GoogleTokenVerifier` interface, `GoogleSignInException`, and
   `GoogleAccountEmailCollisionException` (no-arg constructors, matching how the already-committed
   tests instantiate them).
5. Implement `GoogleAuthService` per the four-step resolution order in the contract.
6. Extend `GlobalExceptionHandler` with handlers for the two new exceptions (401 / 409, fixed
   messages), following the existing `EmailAlreadyRegisteredException`/`InvalidCredentialsException`
   pattern.
7. Extend `AuthController` with the `POST /api/auth/google` endpoint and the `GoogleAuthService`
   constructor dependency.
8. Add `/api/auth/google` to `SecurityConfig`'s `permitAll()` matcher list.
9. Provide a real `GoogleTokenVerifier` implementation (library/HTTP choice is Dev's call per the
   contract's "Out of Scope" section) wired as the Spring bean; not unit tested beyond the interface
   seam, consistent with the test plan.

## Test Coverage Confirmation
All AC-1 through AC-6 are covered by Test's already-committed failing tests, as mapped in
`story-002-test-plan.md`. Dev will add unit tests for:
- The real `GoogleTokenVerifier` implementation's own error handling (malformed token, network
  failure, non-2xx response from Google) at whatever seam that implementation introduces — this is
  below the interface boundary Test's suite mocks, so it's Dev's unit-test responsibility.
- `User.googleUser()` factory: confirms `passwordHash` is null and `googleId`/`email` are set as
  given, mirroring existing `User` construction coverage.
- Flyway migration applies cleanly against the dev Postgres instance (manual verification per the
  test plan's stated approach, matching STORY-001's precedent of no DB-integration test).
