# STORY-002: Google OAuth2 Sign-In

## Prerequisites
- story-001

## As a...
new or returning StreamVault user

## I want to...
sign in using my Google account

## So that...
I can access StreamVault without creating or remembering a separate password

## Acceptance Criteria
- [ ] AC-1: A user can initiate sign-in via a "Sign in with Google" option on the login/registration screen
- [ ] AC-2: On first successful Google sign-in, a StreamVault account is automatically created for that user, linked to their Google account
- [ ] AC-3: On any successful Google sign-in, the user receives a JWT in the same format and with the same session behavior as STORY-001's email/password login
- [ ] AC-4: A user who signs in via Google can only ever read or modify their own library data, never another user's
- [ ] AC-5: Google sign-in failures (denied consent, Google-side error) show a clear, user-facing error and do not create a partial or broken account
- [ ] AC-6: If the Google account's email matches an existing email/password account, sign-in is blocked with the error message: "An account with this email already exists. Please sign in with your password." No account is created or linked.

## Notes
- Account linking (allowing a single account to authenticate via both email/password and Google) is explicitly out of scope by decision — blocking on email collision is simpler and sufficient for a portfolio project. Tracked as a possible future enhancement in `docs/specs/backlog.md`.
- Depends on STORY-001's JWT session format being defined first, since this story reuses it.

## Out of Scope
- Linking/unlinking Google sign-in from an existing email/password account after the fact (see docs/specs/backlog.md)
- Any other OAuth providers (GitHub, etc.)
