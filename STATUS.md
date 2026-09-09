# StreamVault - Project Status

> This file is the source of truth for project health and progress.
> Updated by Brian (infrastructure/review), PO persona (epics/stories), Dev persona (implementation), and Test persona (verification).
> **Updated as part of every meaningful commit - do not let this file fall behind.**

---

## Health Indicator

### Rules
To compute current health, use `Last Updated` date and `Blocked Items` section below:

| Status | Condition |
|---|---|
| 🟢 Green | Last Updated within 3 days AND no blocked items |
| 🟡 Yellow | Last Updated 4-7 days ago OR any blocked items with a plan to unblock |
| 🔴 Red | Last Updated 7+ days ago OR blocked with no plan to unblock |

### Last Updated
2026-09-09

### STORY-005 Status
Test re-verification of the Phase 4 Dev fix on PR #25 (commit 2af535c) complete. Dev's fix is
test-infrastructure only: added `testsupport/WithMockAuthenticatedUser` + its
`WithMockAuthenticatedUserSecurityContextFactory` per `docs/specs/design/story-005-brian-review-r1.md`,
refactored `AccountSettingsControllerTest` off the `@BeforeEach`/`@AfterEach` `SecurityContextHolder`
seeding onto `@WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")`, and reworded one
Javadoc line so the source-scanning guard no longer matches prose. No production code changed by this
fix (diff: STATUS.md + 2 new test-support classes + 2 test files). Full suite green: 77/77 via
`mvn clean verify`. Convention now enforced by `ControllerSliceTestAuthConventionTest` (both halves)
and pinned by `AccountSettingsControllerPrincipalConventionTest`. All AC-1..AC-6 and all cross-story
invariants covered by passing tests. Test APPROVED on PR #25; awaiting Brian's review and merge.

### STORY-006 Status
Phase 1 (Test goes first) complete on branch `feature/story-006-tmdb-search-browse`. Test plan
(`docs/specs/design/story-006-test-plan.md`) maps every AC-1..AC-8 plus cross-story invariants to
named tests; API contracts (`docs/specs/design/story-006-api-contracts.md`) define `GET
/api/tmdb/search` and `GET /api/tmdb/browse`, a shared `TmdbResultPage` / `TmdbResult` projection,
`MOVIE`/`SERIES` and `POPULAR`/`TRENDING` wire enums, 400 validation envelopes, 401 reuse of the
existing entry point, and a new 502 `TmdbUnavailableException` mapping. Six failing test classes
committed: `TmdbControllerTest`, `TmdbCatalogServiceTest`, `RestClientTmdbGatewayTest` (MockRest,
mirrors `GoogleTokenInfoVerifierTest`), `dto/TmdbSearchRequestValidationTest`,
`TmdbEndpointsSecurityTest` (Layer 2 real filter chain, AC-8 + `SecurityConfig` invariant), and
`TmdbPackageReadOnlyConventionTest` (source scan guarding AC-7 read-only + no new migration).
RED confirmed: `mvn clean test-compile` fails only on the nine unimplemented `com.streamvault.backend.tmdb`
symbols, consistent with the story-002/story-005 RED convention. Awaiting Dev design review
(`story-006-dev-feedback-r1.md`). This story adds no Flyway migration and no `SecurityConfig` change.

### Current Phase
Application Development - User Authentication epic complete and merged (STORY-001, STORY-002). Autonomous Agentic Workflow epic complete (STORY-003, STORY-004). Personal Streaming Library epic defined by PO: STORY-005 through STORY-019 specced and queued for Test and Dev.

---

## Infrastructure Tasks

### EC2 / Docker
- [x] EC2 instance created (`streamvault-server`, t3.micro, Ubuntu 24.04, 20GB gp3)
- [x] Elastic IP assigned (`54.166.127.211`)
- [x] Security group configured (SSH/My IP, HTTP/HTTPS public)
- [x] Docker installed on EC2 (v29.6.2)
- [x] Docker Compose installed on EC2 (v5.3.1)
- [x] AWS Budget alarm configured
- [x] Swap file added to EC2 (2GB, persistent via /etc/fstab)
- [x] `restart: unless-stopped` added to all compose services
- [x] PostgreSQL container stable on EC2
- [x] LiteLLM deferred from EC2 (t3.micro memory constraint - revisit when upgrading instance for production demo)
- [x] Caddy reverse proxy added to compose and responding on port 80/443
- [x] Split docker-compose.yml (local dev) and docker-compose.prod.yml (EC2)
- [ ] Domain name pointed at Elastic IP

### Local Dev Environment
- [x] Ollama deferred - AMD RX 7600 XT lacks DirectML support in Ollama Docker image on WSL2/Windows; CPU-only inference not performant enough to justify inclusion. Will revisit if Claude Pro API costs become a concern during development.
- [x] Local LiteLLM deferred alongside Ollama - will revisit when Ollama is unblocked or an alternative local inference path is identified.
- [x] MongoDB Atlas M0 free tier created
- [x] MongoDB Atlas connection string added to .env (local + EC2)

### CI/CD
- [x] GitHub Actions workflow: build on push to main

### Agentic Workflow Infrastructure
- [x] Claude Code Docker image built (`claude-experience-img`)
- [x] PO persona container configured (`streamvault-po.sh`)
- [x] Dev persona container configured (`streamvault-dev.sh`)
- [x] Test persona container configured (`streamvault-test.sh`)
- [x] Shared GitHub deploy key generated and registered
- [x] Per-persona Git identity configured
- [x] CLAUDE.md files in place (project root + all 3 personas)

---

## Application Milestones

- [x] First epic defined by PO (user authentication)
- [x] First story implemented by Dev
- [x] First story verified by Test
- [ ] First AI-powered feature end-to-end (LiteLLM + Ollama)
- [ ] Demoable to an interviewer
- [ ] AWS Bedrock production path confirmed

---

## Epics & Stories

### Epic: User Authentication
Spec: `docs/specs/epic-user-authentication.md` - READY FOR DEV (all open questions resolved by Brian 2026-08-11)

- [x] STORY-001: Email/Password Registration and Login (`docs/specs/story-001-email-password-auth.md`) - merged to main 2026-08-15
- [x] STORY-002: Google OAuth2 Sign-In (`docs/specs/story-002-google-oauth.md`) - merged to main 2026-08-30

Deferred work parked in `docs/specs/backlog.md`: Account Settings, Password Reset & Email Verification, server-side JWT revocation, Google/email account linking.

### Epic: Autonomous Agentic Workflow
Spec: `docs/specs/epic-autonomous-agentic-workflow.md` - READY (assignee: Brian, both stories are infrastructure changes to the persona containers themselves, not Dev persona work)

- [x] STORY-003: Local Build & Test Tooling in Dev and Test Containers (`docs/specs/story-003-dev-test-build-tooling.md`) - tackled first
- [x] STORY-004: GitHub PR Automation for Dev and Test Personas (`docs/specs/story-004-github-pr-automation.md`) - depends on STORY-003

Deferred work parked in `docs/specs/backlog.md`: Testcontainers/Docker-in-Docker for Test persona, GitHub App-based auth.

### Epic: Personal Streaming Library
Spec: `docs/specs/epic-personal-library.md` - awaiting Brian review before the queue picks up STORY-005

- [ ] STORY-005: Account Settings for Rating Type Preference (`docs/specs/story-005-account-settings-rating-type.md`) - prerequisite for STORY-015, tracked outside the epic - Dev implementation complete, Phase 4 Brian-review fix re-verified by Test (77/77 green, all ACs + invariants covered), Test APPROVED on PR #25, awaiting Brian's review and merge
- [ ] STORY-006: TMDB Search and Browse (`docs/specs/story-006-tmdb-search-browse.md`) - Phase 1 complete: test plan, API contracts, and 6 failing test classes on `feature/story-006-tmdb-search-browse`, RED confirmed; awaiting Dev design review
- [ ] STORY-007: Add Movie from TMDB to Library (`docs/specs/story-007-add-movie-from-tmdb.md`) - prereq STORY-006
- [ ] STORY-008: Add TV Series from TMDB to Library (`docs/specs/story-008-add-series-from-tmdb.md`) - prereq STORY-006
- [ ] STORY-009: View and Filter My Library (`docs/specs/story-009-view-filter-library.md`) - prereq STORY-007, STORY-008
- [ ] STORY-010: Set Movie Watch Status (`docs/specs/story-010-set-movie-watch-status.md`) - prereq STORY-007
- [ ] STORY-011: Remove Item from Library (`docs/specs/story-011-remove-library-item.md`) - prereq STORY-007, STORY-008
- [ ] STORY-012: Series Progress by Episode with Season and Series Roll-Up (`docs/specs/story-012-series-episode-status-rollup.md`) - prereq STORY-008
- [ ] STORY-013: Set and Clear Watch Dates Across Series Levels (`docs/specs/story-013-watch-dates.md`) - prereq STORY-010, STORY-012
- [ ] STORY-014: Notes and Review per Library Item (`docs/specs/story-014-notes-review.md`) - prereq STORY-007, STORY-008
- [ ] STORY-015: Personal Rating per Library Item (`docs/specs/story-015-personal-rating.md`) - prereq STORY-005, STORY-007, STORY-008
- [ ] STORY-016: Streaming Source per Library Item from a Predefined List (`docs/specs/story-016-streaming-source-predefined.md`) - prereq STORY-007, STORY-008, STORY-009
- [ ] STORY-017: Custom Streaming Source Entries (`docs/specs/story-017-custom-streaming-source.md`) - prereq STORY-016
- [ ] STORY-018: Refresh a Series from TMDB to Pick Up New Seasons and Episodes (`docs/specs/story-018-refresh-series-from-tmdb.md`) - prereq STORY-008, STORY-012
- [ ] STORY-019: Bypass Removal Confirmation Preference (`docs/specs/story-019-bypass-removal-confirmation.md`) - prereq STORY-011

Deferred work parked in `docs/specs/backlog.md`: grouping and collections (own future epic, unlocks group-level watch date clearing), per-season/per-episode notes and ratings, custom metadata and tags (MongoDB entry point), scheduled background series sync, broader Account Settings epic.

---

## Blocked Items

N/A

---

## Stack Reference

| Layer | Technology |
|---|---|
| Backend | Java 25, Spring Boot 3.5.16, Spring AI |
| Frontend | Next.js 14+, TypeScript |
| Relational DB | PostgreSQL |
| Document DB | MongoDB Atlas (M0 free tier) |
| AI Gateway | LiteLLM |
| Local Inference | Ollama |
| Cloud Inference | AWS Bedrock |
| Infrastructure | AWS EC2 t3.micro, Docker, Docker Compose, Caddy |
| Networking | Tailscale |
| Spec Tooling | OpenSpec, OpenCode |

---

## Key References

| Resource | Detail |
|---|---|
| GitHub Repo | https://github.com/birdman74/streamvault |
| EC2 Elastic IP | 54.166.127.211 |
| SSH Key | C:\Users\brian\.ssh\streamvault-key.pem |
| EC2 User | ubuntu |
| License | All Rights Reserved |

---

## Architecture Decisions

| Decision | Rationale |
|---|---|
| Dual-store (PostgreSQL + MongoDB) | PostgreSQL for structured relational data (users, watch history); MongoDB for flexible media metadata where schema varies significantly (home movies vs TMDB entries) |
| LiteLLM as AI gateway | Provider-agnostic routing so application code never changes when switching between Ollama (free local dev) and AWS Bedrock (production demos) |
| LiteLLM deferred from EC2 | t3.micro has 1GB RAM; LiteLLM consumed ~500MB leaving insufficient headroom for Spring Boot. Will revisit on instance upgrade. |
| Spring AI over direct SDK | First-class Java abstraction for AI that enterprise Java shops are adopting; demonstrates modern Java AI integration patterns |
| Single Claude Code image, three personas | Tooling needs are identical across personas; behavior is driven entirely by CLAUDE.md system prompts |
| All Rights Reserved license | Portfolio repo must be publicly visible for recruiters while protecting original work |
| Ollama deferred (local) | AMD RX 7600 XT GPU passthrough to Docker on WSL2/Windows uses DirectML which Ollama does not support. CPU-only inference is too slow for practical use. LiteLLM will route to Claude API during development and AWS Bedrock for production demos. Will revisit if Claude Pro quota or API costs become a concern. |
