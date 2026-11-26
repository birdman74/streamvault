# Backlog / Deferred Work

Placeholders for work explicitly identified but deferred out of an active epic's scope. These are not yet specced as epics or stories: they're parking spots so the context isn't lost. Pull from this list when scoping future epics.

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

## TMDB Search Types Beyond Movie and TV (deferred from Epic: Personal Streaming Library, 2026-09-10)
STORY-006 deliberately narrows TMDB search and browse to movie and TV series results only. TMDB's combined search also returns people, and TMDB additionally exposes dedicated search endpoints for other entity types. STORY-006 discards everything that is not a movie or a series before building a response, because the epic's job is to get titles into a personal library and nothing else consumes these other types yet. Each deferred search type below is a candidate for its own story, or a small "discovery" epic, once there is a feature that actually uses it:

- Person search (actors, directors, crew). Explicitly called out and excluded from STORY-006. Value: let a user find a title by who appears in it or who made it, or browse a person's filmography as a way into the catalog. Most likely first pull from this list.
- Collection search (named franchises and groupings, for example "The Matrix Collection"). Overlaps with the "Grouping and Collections" item above; if that epic uses TMDB collections to seed user-defined groups, this search becomes a prerequisite for it.
- Company search (production and distribution companies, for example A24, Pixar). Value: browse or filter the catalog by studio.
- Keyword search (TMDB's controlled tag vocabulary, for example "based on novel", "dystopia"). Value: thematic discovery beyond free-text title matching.

None of these change what STORY-006 delivers. Pull from this list only when a concrete feature needs one of them; do not spec them speculatively.

## AI and Infrastructure Tooling

Tools named in the original StreamVault stack plan (see `ai-dev-toolkit` DEVLOG, Phase 0) that are not yet used by the application. Each entry records why it is deferred and what should trigger the work. When one is pulled, spec it as an infrastructure story (assignee: Brian, like STORY-003 and STORY-004) or as the enabling first story of a feature epic.

### Spring AI (deferred, 2026-11-26)
Listed as the backend AI layer, but no AI feature exists yet, so it is not in `pom.xml`. Trigger: the first feature that needs an LLM (for example recommendations, natural-language library search, or summarizing a user's notes). Scope: add Spring AI, wire it to the AI gateway below, and prove it with one real feature behind a story with acceptance criteria. Why it matters: demonstrates the Java-native AI integration that enterprise Java teams are adopting.

### LiteLLM AI Gateway (partially in place, deferred from EC2, 2026-11-26)
A LiteLLM container is defined in the local `docker-compose.yml` with `infrastructure/litellm-config.yml`, but no application code calls it, and it is deliberately excluded from `docker-compose.prod.yml` because the t3.micro (1 GB RAM) cannot run it next to Spring Boot. Trigger: the Spring AI story above, or an EC2 instance upgrade. Scope: route Spring AI through LiteLLM so application code never changes between local and cloud models; decide the production host (larger instance, or a managed alternative) and its cost.

### Ollama Local Inference (blocked, 2026-11-26)
Free local inference for development. Blocked: the AMD RX 7600 XT on WSL2 Docker needs DirectML, which Ollama does not support, and CPU-only inference is too slow. Trigger: a supported GPU path (ROCm on WSL2 or native), different hardware, or API costs becoming a concern. Scope: run Ollama locally behind LiteLLM and confirm the Spring AI feature works against it.

### AWS Bedrock Cloud Inference (deferred, 2026-11-26)
Intended for production demos through LiteLLM. Not set up: no AWS Bedrock model access, IAM policy, or LiteLLM route exists yet. Trigger: the first AI feature is ready to demo from EC2. Scope: request model access, add a least-privilege IAM role for the instance, add the LiteLLM route, and set a budget alarm, since AWS free tier has expired.

### Tailscale Private Network (parked, 2026-11-26)
Would let the EC2 app reach local Ollama without public exposure. Only useful once Ollama works and a cloud-deployed feature needs local inference. Trigger: Ollama unblocked. Scope: tailnet with the EC2 instance and the local machine, ACLs limited to the LiteLLM port.

### Spec-Driven Development Tooling: OpenSpec and OpenCode (evaluation, 2026-11-26)
Planned in Phase 0 as the spec-driven development framework but never adopted: specs are written by the PO persona in Claude Code, following its CLAUDE.md, with acceptance criteria labeled AC-N and prerequisites the queue parses. Scope: evaluate whether OpenSpec (and OpenCode) would improve on that, comparing spec format, change tracking, how Test and Dev consume specs, and fit with the pipeline's file conventions (`docs/specs/story-NNN-*.md`, `## Prerequisites`, AC-N). Outcome: an ADR either adopting one (with a migration plan for PO's CLAUDE.md and the queue scripts) or recording why the current approach stays.