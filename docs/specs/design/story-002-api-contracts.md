# API Contracts — story-002: Google OAuth2 Sign-In

## Overview

Adds a single new endpoint, `POST /api/auth/google`, that unifies sign-up and sign-in for
Google-authenticated users. It reuses STORY-001's `AuthResponse` shape and JWT format so that
downstream consumers (frontend, any future endpoints) treat a Google-issued session identically
to an email/password session.

The frontend is responsible for driving the Google OAuth consent flow (Google Identity Services)
and obtaining a Google-issued ID token. The backend's only job is to verify that ID token, resolve
or create the corresponding StreamVault user, and issue a StreamVault JWT. The backend never talks
to Google directly during the redirect/consent flow.

## Endpoint: POST /api/auth/google

### Request

```json
{
  "idToken": "eyJhbGciOi..."
}
```

| Field | Type | Rules |
|---|---|---|
| idToken | string | required, not blank. The Google-issued ID token (JWT) from Google Identity Services on the frontend. |

### Response — 200 OK (success, both new and returning users)

```json
{
  "token": "<streamvault-jwt>",
  "tokenType": "Bearer"
}
```

Identical shape to STORY-001's `POST /api/auth/login` response (`AuthResponse`). No field
distinguishes a newly created account from a returning one — the AC only requires that both cases
succeed and return a usable session; the UI does not need to branch on it.

The JWT itself carries the same claims as STORY-001 (`sub`=email, `userId`, `iat`, `exp`) so that
`JwtAuthenticationFilter` and `AuthenticatedUser` require no changes. Because the userId claim is
resolved from the single StreamVault user row matching this Google account, downstream endpoints
that scope data by `AuthenticatedUser.userId()` are correctly isolated per AC-4 with no additional
authorization logic required in this story.

### Response — 400 Bad Request (validation failure)

Reuses STORY-001's existing `MethodArgumentNotValidException` handler format.

```json
{
  "error": "Validation failed",
  "fields": {
    "idToken": "ID token is required"
  }
}
```

### Response — 401 Unauthorized (Google sign-in failed)

Covers: denied consent surfaced to the backend, an invalid/expired/unparseable ID token, or any
Google-side verification error.

```json
{
  "error": "Google sign-in failed. Please try again."
}
```

No user account is created or modified in this path (AC-5).

### Response — 409 Conflict (email collision with existing local account)

```json
{
  "error": "An account with this email already exists. Please sign in with your password."
}
```

Returned when the verified Google email matches an existing email/password account (a user row
with a non-null `password_hash` and no linked `google_id`). No account is created or linked (AC-6).

## New Components (contract for Dev to implement against)

These do not exist yet. Tests in this branch are written against these names and will fail to
compile until Dev implements them — that failure to compile/run is the expected RED state for this
story.

### `com.streamvault.backend.auth.dto.GoogleSignInRequest`
```java
record GoogleSignInRequest(@NotBlank(message = "ID token is required") String idToken)
```
`toString()` must not need redaction (ID tokens are single-use and short-lived, but out of an
abundance of caution the contract still expects a custom `toString()` that omits the raw token
value, matching the redaction pattern already used by `LoginRequest`/`RegisterRequest`).

### `com.streamvault.backend.auth.GoogleUserInfo`
```java
record GoogleUserInfo(String googleId, String email, boolean emailVerified)
```
Represents the verified identity claims extracted from a Google ID token (`sub`, `email`,
`email_verified`).

### `com.streamvault.backend.auth.GoogleTokenVerifier`
```java
interface GoogleTokenVerifier {
    GoogleUserInfo verify(String idToken); // throws GoogleSignInException on any failure
}
```
Seam between `GoogleAuthService` and the actual Google token verification mechanism (e.g. Google's
tokeninfo endpoint or `google-api-client`'s `GoogleIdTokenVerifier`). Kept as an interface so
`GoogleAuthService` can be unit tested without network calls or real Google credentials.

### `com.streamvault.backend.auth.exception.GoogleSignInException`
`RuntimeException`, thrown by `GoogleTokenVerifier` implementations and caught nowhere else —
propagates to `GlobalExceptionHandler`, mapped to 401 with the fixed message above.

### `com.streamvault.backend.auth.exception.GoogleAccountEmailCollisionException`
`RuntimeException`, thrown by `GoogleAuthService` when the verified email belongs to an existing
local (email/password) account. Mapped to 409 with the fixed message above (AC-6 requires this
exact string).

### `com.streamvault.backend.auth.GoogleAuthService`
```java
class GoogleAuthService {
    GoogleAuthService(UserRepository userRepository, JwtService jwtService, GoogleTokenVerifier googleTokenVerifier);
    AuthResponse googleSignIn(GoogleSignInRequest request); // @Transactional
}
```

Resolution order:
1. Call `googleTokenVerifier.verify(request.idToken())`. Any exception propagates as-is
   (`GoogleSignInException`) — no account is touched.
2. Look up `userRepository.findByGoogleId(googleId)`. If found, issue a JWT for that user
   (returning-user path). No new row is written.
3. Else look up `userRepository.findByEmail(email)`.
   - If found and it is a local account (`passwordHash != null`), throw
     `GoogleAccountEmailCollisionException`. No account is created or linked.
   - If not found, create a new `User` via `User.googleUser(email, googleId)`, save it, and issue
     a JWT for it (first-sign-in path, AC-2).
4. The verify-then-resolve-then-save sequence runs inside a single transaction so that a failure
   partway through (e.g. save fails) never leaves a partial account (AC-5).

Kept as a separate service from STORY-001's `AuthService` (rather than extending it) so the
existing `AuthService` constructor and its STORY-001 test suite are untouched by this story.

### `com.streamvault.backend.auth.AuthController` (extended)
```java
@PostMapping("/google")
public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleSignInRequest request) {
    return ResponseEntity.ok(googleAuthService.googleSignIn(request));
}
```
`AuthController`'s constructor gains a `GoogleAuthService` dependency alongside the existing
`AuthService`.

### `com.streamvault.backend.user.User` (extended)
- `passwordHash` becomes nullable (Google-only accounts have none).
- New nullable, unique `googleId` field.
- New factory `static User googleUser(String email, String googleId)` — sets `passwordHash` to
  `null`.
- Existing `User(String email, String passwordHash)` constructor is unchanged so STORY-001 code
  and tests are unaffected.

### `com.streamvault.backend.user.UserRepository` (extended)
```java
Optional<User> findByGoogleId(String googleId);
```

### `com.streamvault.backend.config.SecurityConfig` (extended)
`/api/auth/google` added to the existing `permitAll()` matcher list alongside `/api/auth/register`
and `/api/auth/login`.

### Database migration (new)
`V2__add_google_id_to_users.sql`:
```sql
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN google_id VARCHAR(255) UNIQUE;
```

## Out of Scope for this contract
- The actual Google token verification implementation (which library/HTTP call `GoogleTokenVerifier`'s
  real implementation uses) is a Dev implementation decision, not part of this contract — the
  interface is the seam.
- Account linking (see `docs/specs/backlog.md`).
- Frontend OAuth consent flow / Google Identity Services integration details.
