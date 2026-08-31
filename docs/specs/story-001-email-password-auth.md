# STORY-001: Email/Password Registration and Login

## As a...
new or returning StreamVault user

## I want to...
register an account with my email and password, and log in with those credentials

## So that...
I can securely access my own personal streaming library, separate from every other user's data

## Acceptance Criteria
- [ ] A user can register with an email address and password; the email must be unique across all accounts
- [ ] Registration fails with a clear, user-facing error if the email is already registered
- [ ] Registration fails with a clear, user-facing error if the password is under 8 characters, or does not contain at least one uppercase letter, one lowercase letter, and one number
- [ ] User passwords are never stored or exposed in plain text, in any API response, log, or database field
- [ ] A registered user can log in with correct email and password and receives a JWT representing their session
- [ ] Login fails with a generic invalid-credentials error for wrong email or wrong password, without indicating which one was incorrect
- [ ] A logged-in user can log out, which discards their JWT client-side
- [ ] All authenticated endpoints reject requests without a valid JWT
- [ ] A user can only ever read or modify their own library data, never another user's

## Notes
- Logout is client-side token discard only for this story — there is no server-side revocation, so a JWT technically remains valid (per its expiration) even after logout. This is a known limitation, not a bug. Server-side revocation (token blacklist via Redis or a DB table) is a future enhancement, tracked in `docs/specs/backlog.md`.
- Password complexity: minimum 8 characters, at least one uppercase letter, one lowercase letter, one number. No special character requirement by decision (adds friction without meaningful security benefit at this scale).
- Rate limiting or lockout behavior on repeated failed logins is not addressed here; flagging as a possible future security hardening item, not blocking this story.

## Out of Scope
- Password reset / forgot password flow
- Email verification
- Changing password or deleting account (see docs/specs/backlog.md)
- Any role or permission beyond "owner of my own data"

## Prerequisites: - None
