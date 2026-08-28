# Test Plan — story-002: Google OAuth2 Sign-In

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | User can initiate sign-in via "Sign in with Google" on login/registration screen | Frontend UI concern; no backend test. Backend contract this AC depends on (`POST /api/auth/google` exists and accepts an ID token) is covered by `AuthControllerGoogleTest.should_return200WithToken_when_googleSignInSucceedsForNewUser` and the sibling returning-user test. |
| AC-2 | On first successful Google sign-in, a StreamVault account is automatically created, linked to the Google account | `GoogleAuthServiceTest.should_createNewUserLinkedToGoogleId_when_noExistingAccountMatchesEmailOrGoogleId` |
| AC-3 | On any successful Google sign-in, user receives a JWT in the same format/session behavior as STORY-001 login | `GoogleAuthServiceTest.should_returnBearerTokenInAuthResponse_when_signInSucceeds` (new-user path) and `should_returnTokenForExistingGoogleLinkedUser_when_returningUserSignsIn` (returning-user path); `AuthControllerGoogleTest.should_return200WithToken_when_googleSignInSucceedsForNewUser` verifies the wire shape (`token`, `tokenType":"Bearer"`) matches STORY-001's `/api/auth/login` response. |
| AC-4 | A Google-signed-in user can only ever read/modify their own library data, never another user's | `GoogleAuthServiceTest.should_issueTokenBoundToTheMatchedUsersId_when_returningGoogleUserSignsInAgain` (proves repeated sign-in resolves to the same userId, not a new one, so the existing `JwtAuthenticationFilter`/`AuthenticatedUser` scoping from STORY-001 is correctly keyed). No new authorization logic is introduced by this story (no library endpoints exist yet); full data-isolation testing is the responsibility of whichever story introduces library endpoints. |
| AC-5 | Google sign-in failures (denied consent, Google-side error) show a clear, user-facing error and do not create a partial/broken account | `GoogleAuthServiceTest.should_propagateGoogleSignInException_when_tokenVerificationFails` + `should_neverCallSaveOnUserRepository_when_tokenVerificationFails`; `AuthControllerGoogleTest.should_return401WithClearErrorMessage_when_googleTokenVerificationFails` |
| AC-6 | If Google account's email matches an existing email/password account, sign-in is blocked with the exact message, no account created/linked | `GoogleAuthServiceTest.should_throwGoogleAccountEmailCollisionException_when_emailMatchesExistingLocalAccount` + `should_neverCallSaveOnUserRepository_when_emailCollisionWithLocalAccountOccurs`; `AuthControllerGoogleTest.should_return409WithExactCollisionMessage_when_emailMatchesExistingLocalAccount` |

## API Contracts

See `story-002-api-contracts.md`.

## Test Strategy

- **Unit tests (`GoogleAuthServiceTest`)**: Mockito-based, mirroring the existing
  `AuthServiceTest` style. Mocks `UserRepository`, `JwtService`, and the new `GoogleTokenVerifier`
  seam so no real Google network call or credentials are needed. This is where the bulk of AC
  coverage lives (AC-2, AC-3, AC-4, AC-5, AC-6).
- **DTO validation test (`GoogleSignInRequestValidationTest`)**: plain Jakarta Validator test,
  mirroring `RegisterRequestValidationTest`, confirming a blank `idToken` is rejected.
- **Controller slice test (`AuthControllerGoogleTest`)**: `@WebMvcTest(AuthController.class)` with
  `@AutoConfigureMockMvc(addFilters = false)`, mirroring `HealthControllerTest`. Mocks
  `GoogleAuthService` (and `AuthService`/`JwtService` as needed for context loading) to verify the
  HTTP-layer contract: status codes and JSON response/error bodies for the 200/400/401/409 cases
  defined in the API contract.
- No integration/Testcontainers-backed test touches the real `users` table or the new
  `V2__add_google_id_to_users.sql` migration — consistent with STORY-001, which also has no
  DB-integration test. Migration correctness is verified manually against the dev Postgres
  instance during Phase 3 verification.
- Security-filter-chain behavior (that `/api/auth/google` is actually reachable unauthenticated
  through the real `SecurityConfig`, not just in a slice test with filters disabled) is out of
  scope for automated coverage in this story, matching the existing gap for
  `/api/auth/login` and `/api/auth/register` in STORY-001.

## Out of Scope

- Frontend "Sign in with Google" button/flow (AC-1's UI half) — no frontend test project exists
  yet in this repo.
- Real Google ID token verification logic/library choice — `GoogleTokenVerifier` is mocked at the
  interface boundary in all tests; its real implementation is Dev's to design and is not unit
  tested here beyond the interface contract.
- Account linking, other OAuth providers — explicitly out of scope per the story and
  `docs/specs/backlog.md`.
- Full authorization/data-isolation testing for AC-4 beyond JWT userId binding, since no library
  data endpoints exist in the codebase yet.
