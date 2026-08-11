# Backlog / Deferred Work

Placeholders for work explicitly identified but deferred out of an active epic's scope. These are not yet specced as epics or stories — they're parking spots so the context isn't lost. Pull from this list when scoping future epics.

## Account Settings (deferred from Epic: User Authentication, 2026-08-11)
Change password, delete account, update profile/email. Deferred because STORY-001/STORY-002 cover registration and login only; account management is a separate concern.

## Password Reset & Email Verification (deferred from Epic: User Authentication, 2026-08-11)
Forgot-password flow and email verification on registration. Deferred because email-sending infrastructure is not yet part of the stack and Brian wants this out of the first epic's scope.

## Server-Side JWT Revocation (deferred from Epic: User Authentication, 2026-08-11)
STORY-001's logout is client-side token discard only; a JWT remains technically valid until expiry even after logout. True server-side revocation (token blacklist via Redis or a DB table) is deferred as additional infrastructure not justified for the first epic.

## Account Linking: Google + Email/Password (decided, deferred from Epic: User Authentication, 2026-08-11)
Decision: blocked, not linked. If a Google sign-in email matches an existing email/password account, STORY-002 blocks sign-in with an error directing the user to sign in with their password. True account linking (letting one account use both methods) is a non-trivial UX flow, deferred as a possible future enhancement.

## Testcontainers / Docker-in-Docker for Test Persona (deferred from Epic: Autonomous Agentic Workflow, 2026-08-11)
STORY-003 gives the Test persona container Java/Maven/Node tooling for unit and Spring MockMvc tests only. Running Testcontainers-based integration tests would require mounting the host Docker socket (or Docker-in-Docker) into the container, which is a real host-access tradeoff. Brian wants to revisit this once there's a clearer security posture for it, rather than bake it into this epic.

## GitHub App-Based Auth for Personas (decided, deferred from Epic: Autonomous Agentic Workflow, 2026-08-11)
Decision: use a fine-grained personal access token scoped to the `streamvault` repo instead. A GitHub App is more setup than a personal project needs; revisit only if multi-repo or org-level automation becomes necessary.
