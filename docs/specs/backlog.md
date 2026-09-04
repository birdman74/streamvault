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

## Grouping and Collections (deferred from Epic: Personal Streaming Library, 2026-08-31)
User-defined groups and named libraries: franchises (MCU, Pixar), directors or characters (Zatoichi), and multiple separate libraries per user. Meaningful feature but it pulls in enough scope and cross-story dependencies to warrant its own epic. It also unlocks the deferred "clear watch date at the group level" capability that STORY-013 leaves out. Spec as its own epic after the core library epic lands.

## Per-Season and Per-Episode Notes and Ratings (deferred from Epic: Personal Streaming Library, 2026-08-31)
STORY-014 and STORY-015 keep notes and ratings at the library-item level only (a movie or a whole series). Letting a user rate or annotate an individual season or episode is a natural extension once item-level behavior is proven. Deferred to keep the first library epic small.

## Rating Type Switch Behavior for Existing Ratings (decided, 2026-09-04)
Decision by Brian: when a user changes their account rating type (STORY-005) after ratings already exist, the existing stored ratings are kept exactly as they are and only new writes are validated against the newly selected type. No conversion, no clearing. Now covered by STORY-015 AC-9; kept here as the record of the decision.

## Scheduled Background Series Sync (deferred from Epic: Personal Streaming Library, 2026-09-04)
STORY-018 delivers user-triggered refresh and a staleness-triggered refresh on access when the user has opted in. It deliberately stops short of an always-on scheduler that walks every user's library on a cron. That scheduler is a real recurring-compute and TMDB-call cost against a stopped-when-idle EC2 instance, so it is deferred until there is a reason and a budget for it.

## Custom Metadata and Tags for Library Items (deferred from Epic: Personal Streaming Library, 2026-09-04)
Let users attach their own tags or key/value metadata to library items (for example "rewatch", "with the kids", "recommended by Sam"), and filter the STORY-009 library view by them. This is also the first place user-entered variable-schema data enters the product, so it is the natural trigger for introducing MongoDB per the epic's data-store direction. Spec as its own story or small epic after the core library epic lands.

## Account Settings Epic (carved out during Epic: Personal Streaming Library scoping, 2026-08-31)
STORY-005 delivers only the rating type preference because the library epic needs it. The broader account-management surface, change password, delete account, update profile or email, remains unspecced. Promote the "Account Settings" item above into a full epic when this work is prioritized.
