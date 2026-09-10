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
2026-09-10

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
Phase 3 (Test final PR verification) complete on PR #26, branch
`feature/story-006-tmdb-search-browse`: **APPROVED**. Pulled the branch and ran `mvn clean verify` -
full suite green 147/147, JaCoCo 75% instruction gate passes. Every AC-1..AC-8 is covered by at
least one passing test and every cross-story invariant holds. Regression analysis of the diff: the
only shared-runtime change is one additive `@ExceptionHandler(TmdbUnavailableException.class)` in
`GlobalExceptionHandler` (no existing mapping altered; the other branches stay covered by
`AccountSettingsControllerTest` and `AuthControllerGoogleTest`, both green); `application.yml` gains
a `tmdb.*` block whose `api-key` has no default, matching the existing `GOOGLE_CLIENT_ID` /
`JWT_SECRET` precedent, and the context-load smoke test (`StreamvaultBackendApplicationTests`) plus
the two other full-context `@SpringBootTest` classes pass with the `tmdb.api-key`
`@DynamicPropertySource` entry Dev added; no DB schema, `SecurityConfig`, repository, or shared
service was touched (`TmdbPackageReadOnlyConventionTest` pins the migration set at V1..V4 and the
`TmdbEndpointsSecurityTest` boundary tests confirm `/api/health` and `/api/auth/me` are unmoved). No
new regression tests were required - every shared-infrastructure change is additive and already
covered by an existing passing test. Awaiting Brian's review and merge.

Phase 2 (Dev implementation) record: New `com.streamvault.backend.tmdb` package: `TmdbController`
(`GET /api/tmdb/search`, `GET /api/tmdb/browse`), `TmdbCatalogService` (owns `page` null -> 1 and
`list` null -> `POPULAR` defaults, no persistence collaborator), `TmdbGateway` +
`RestClientTmdbGateway` (v3 `api_key` query param, `/search/multi` and `/trending/all/{week,day}`,
`movie`/`tv` -> `MOVIE`/`SERIES` with `person` dropped, leading-4-digit year parse, absolute
`posterUrl`, every `RestClientException`/non-2xx wrapped in `TmdbUnavailableException`), the two
wire enums, the `TmdbResult`/`TmdbResultPage` projection, `TmdbSearchRequest`/`TmdbBrowseRequest`
query-bound validated records, and `TmdbUnavailableException`. `GlobalExceptionHandler` gains one
additive `@ExceptionHandler(TmdbUnavailableException.class)` -> 502 with the fixed message (cause
logged, never in body). No `SecurityConfig` change (routes fall under `anyRequest().authenticated()`);
no entity, repository, or Flyway migration (set stays V1..V4). Config: `application.yml` gains
`tmdb.api-key`/`base-url`/`image-base-url`; `.env.example` gains `TMDB_IMAGE_BASE_URL`. The three
`tmdb.*` props follow the agreed contract's no-default form for `api-key` (consistent with
`GOOGLE_CLIENT_ID`), so the pre-existing full-context `@SpringBootTest` classes
(`StreamvaultBackendApplicationTests`, `SecurityConfigAuthFlowTest`, `UserTableConstraintsTest`)
each add `tmdb.api-key` to their existing `@DynamicPropertySource` block, mirroring how they
already supply `app.jwt.secret` / `app.google.client-id`. Dev added lower-level unit tests below
Test's integration boundary: `RestClientTmdbGatewayEdgeCasesTest` (year-parse edge cases, payload
ordering after the person drop, absent/null `results` -> `List.of()`), `TmdbWireEnumTest`
(enum `name()` round-trip), `TmdbUnavailableExceptionTest` (cause retention). Full suite green:
147/147 via `mvn clean verify` (JaCoCo 75% gate passes). All six Test-authored failing classes now
pass. No deviation from the agreed design's non-obvious framework point: the implicit
`@ModelAttribute` constructor binding routes `@NotBlank`/`@Min`/`@Max` and the `list` type-mismatch
through the existing `MethodArgumentNotValidException` handler exactly as predicted; no new handler
branch was needed.

### STORY-006 Phase 1 Record
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
symbols, consistent with the story-002/story-005 RED convention. This story adds no Flyway migration
and no `SecurityConfig` change.

Phase 1 design review complete: Dev agreed on round 1 (`docs/specs/design/story-006-agreed.md`), no
blocking concerns against AC-1..AC-8 or the cross-story invariants. One non-obvious framework point
recorded for Brian: `TmdbSearchRequest` / `TmdbBrowseRequest` are the first query-string-bound
validated request objects in this codebase (implicit `@ModelAttribute` constructor binding,
Spring 6.1+), and validation failures are expected to route through the existing
`MethodArgumentNotValidException` handler; Dev will flag to Brian rather than adding an undocumented
handler branch if implementation shows otherwise. Next: Dev implementation - unit tests first, then
make Test's six failing classes plus the new Dev unit tests pass under `mvn clean verify`.

### STORY-007 Status
Phase 3 (Test final PR verification) complete on branch `feature/story-007-add-movie-from-tmdb`,
PR #28 against `main` - **APPROVED**. Pulled the branch, ran `mvn clean verify`: full suite green
**225/225**, 0 failures, 0 errors, JaCoCo 75% instruction gate met ("All coverage checks have been
met"). Every AC-1..AC-7 and every cross-story invariant is covered by at least one passing test.

Regression analysis of the Dev diff (three shared-infrastructure touch points, all additive):
`GlobalExceptionHandler` gained three `@ExceptionHandler` methods with no existing mapping altered -
existing mappings stay green via `AccountSettingsControllerTest`, `AuthControllerGoogleTest`,
`TmdbControllerTest`; `TmdbGateway` gained `movie(long)` with `search` / `browse` untouched -
`RestClientTmdbGatewayTest`, `TmdbCatalogServiceTest`, `TmdbControllerTest`,
`TmdbEndpointsSecurityTest` all re-ran green; `V5__create_library_movies_table.sql` is the first FK
into `users` - every `@SpringBootTest` context load runs Flyway + `ddl-auto=validate` and passed,
`UserTableConstraintsTest` green, and no user-deletion path exists anywhere in the codebase so no
cascade regression is reachable yet (recorded as a cross-story invariant to revisit when
STORY-011 / account deletion lands). No additional regression tests were required; the additive
changes are fully covered by the Phase 1 tests plus the pre-existing suite re-running green.

Dev's flagged deviation (`@Transactional` on Test's `LibraryMovieRepositoryTest` and
`LibraryMoviesTableConstraintsTest`) reviewed and accepted: it fixes a real test-isolation defect
in the Phase 1 tests (fixed-email `users` seed in `@BeforeEach` collided across methods sharing one
cached context and one in-memory H2 database). Spring's standard per-method auto-rollback; no
assertion, datasource, or intent changed; schema-level violations under test are raised
synchronously by H2 so rollback does not mask them. Both classes green (4/4, 6/6) in isolation and
in the full run. Minor note, not a blocker: with `@Transactional` the repository round-trip test
saves and reads within one persistence context, so `status` column read-back fidelity is proven
instead by `LibraryEndpointsSecurityTest` (end-to-end status persist) and the `validate` context
load.

AC-6 "and see": implemented as an isolation guarantee (add path only, every write bound to
`principal.userId()`, every repository access user-scoped, cross-user invisibility proven by test);
the user-facing read surface is formally deferred to STORY-009. This interpretation was surfaced to
Brian in Phase 1 and agreed in `story-007-agreed.md`; called out again here so Brian makes the
final call at merge.

New `com.streamvault.backend.library` package: `WatchStatus` (`PLANNED` / `CURRENTLY_WATCHING` /
`WATCHED`, plain enum), `LibraryMovie` `@Entity` -> `library_movies` (`IDENTITY` id, `user_id`
NOT NULL, `@Enumerated(STRING)` status, `added_at` mapped to `Instant` exactly as
`User.createdAt`; public all-args constructor stamps `addedAt`, `protected` no-arg for JPA, getters
only), `LibraryMovieRepository` (`existsByUserIdAndTmdbId` + `findByUserIdAndTmdbId`, every access
user-scoped), `dto/AddMovieRequest` (`@NotNull @Positive Long tmdbId`, unconstrained `String
status`), `dto/LibraryMovieResponse` (`status` as `WatchStatus.name()`), `exception/`
`InvalidWatchStatusException` (message from `WatchStatus.values()`, mirrors
`InvalidRatingTypeException`) and `DuplicateLibraryMovieException` (fixed message),
`LibraryMovieService` (constructor `(LibraryMovieRepository, TmdbGateway)`; `addMovie` runs the
agreed five-step order - parse status, duplicate pre-check before TMDB, `tmdbGateway.movie`,
`save` with `DataIntegrityViolationException` -> `DuplicateLibraryMovieException` race backstop,
map saved row), `LibraryMovieController` (`POST /api/library/movies`, owning id always
`principal.userId()`).

TMDB package extended (still persistence-free): `dto/TmdbMovie`, `exception/`
`TmdbTitleNotFoundException` (persistence-free, retains `tmdbId`), `TmdbGateway.movie(long)`, and
`RestClientTmdbGateway.movie(long)` - its own small fetch (not the `TmdbResponse`-typed shared
`fetch` helper) with a two-branch catch: `HttpClientErrorException.NotFound` ->
`TmdbTitleNotFoundException`, every other `RestClientException` / non-2xx -> `TmdbUnavailableException`;
reuses `parseYear` / `imageBaseUrl`; `search` / `browse` byte-for-byte unchanged.
`GlobalExceptionHandler` gains three additive `@ExceptionHandler` methods (400
`InvalidWatchStatusException`, 409 `DuplicateLibraryMovieException`, 404 `TmdbTitleNotFoundException`);
no existing mapping altered, `TmdbUnavailableException` -> 502 reused unchanged.
`V5__create_library_movies_table.sql` added exactly as contracted (`BIGSERIAL` id, `user_id BIGINT
NOT NULL REFERENCES users (id)`, `status VARCHAR(30) NOT NULL`, `added_at TIMESTAMP NOT NULL
DEFAULT now()`, `CONSTRAINT uq_library_movies_user_tmdb UNIQUE (user_id, tmdb_id)`). No
`SecurityConfig`, `application.yml`, or `.env.example` change.

Dev lower-level unit tests added below Test's integration boundary:
`InvalidWatchStatusExceptionTest`, `DuplicateLibraryMovieExceptionTest`,
`TmdbTitleNotFoundExceptionTest` (message constants + `tmdbId` retention),
`LibraryMovieTest` (entity: `addedAt` stamped, `id` null pre-persist, `protected` no-arg ctor,
getters only), `LibraryMovieServiceOrderingTest` (unsupported status rejected before the duplicate
check with `verifyNoInteractions`; response built from the saved entity carrying `id` / `addedAt` /
`status` name), `RestClientTmdbGatewayMovieEdgeCasesTest` (403 stays unavailable; empty `{}` body
-> null fields, no NPE).

Deviation from the agreed design (test-mechanics only, flagged for Test's Phase 3 review): the two
Test-authored `@SpringBootTest` classes `LibraryMovieRepositoryTest` and
`LibraryMoviesTableConstraintsTest` seed fixed-email `users` rows in `@BeforeEach` with no
rollback. The class shares one cached context and one in-memory H2 database across methods, so from
the second method on the seed collided on `users.email` (`DataIntegrityViolationException` in
`setUp` / `seedUsers`), failing 8 of their combined 10 methods regardless of production code. Fix
applied: `@Transactional` on both classes (Spring's standard per-method auto-rollback) plus a
Javadoc line explaining why. No assertion, datasource, or test intent changed; the schema-level
violations under test are raised synchronously by H2 at statement execution so the surrounding
rollback does not mask them. Both classes green in isolation (4/4 and 6/6) and in the full run.

Spec interpretation unchanged from Phase 1: AC-6 "and see" is implemented as an isolation
guarantee (add path only; STORY-009 owns the read surface) pending Brian's call.

### STORY-007 Phase 1 Record
Phase 1 (Test goes first) complete on branch `feature/story-007-add-movie-from-tmdb`, cut from an
up-to-date `main` (STORY-006 merged, PR #26, commit 33fb2a1). Test plan
(`docs/specs/design/story-007-test-plan.md`) maps every AC-1..AC-7 plus the cross-story invariants
to named tests; API contracts (`docs/specs/design/story-007-api-contracts.md`) define
`POST /api/library/movies` (`AddMovieRequest` -> `LibraryMovieResponse`), the shared
`WatchStatus` enum (`PLANNED` default / `CURRENTLY_WATCHING` / `WATCHED`), the `library_movies`
table + `V5__create_library_movies_table.sql` migration (`UNIQUE (user_id, tmdb_id)`, `user_id`
NOT NULL FK to `users`), a new `TmdbGateway.movie(long)` returning `TmdbMovie` with a
`TmdbTitleNotFoundException` on a TMDB 404, and three additive `GlobalExceptionHandler` mappings
(400 invalid status, 409 duplicate, 404 not found). No `SecurityConfig` change:
`/api/library/**` falls under the existing `anyRequest().authenticated()` rule.

Ten failing test classes committed: `dto/AddMovieRequestValidationTest`, `WatchStatusTest`,
`LibraryMovieServiceTest` (Mockito, the add algorithm), `RestClientTmdbGatewayMovieTest`
(MockRestServiceServer, `GET /movie/{id}` mapping + the 404-vs-outage split),
`LibraryMovieControllerTest` and `LibraryMovieControllerPrincipalConventionTest`
(`@WebMvcTest` slice per ADR-001), `LibraryMoviesTableConstraintsTest` and
`LibraryMovieRepositoryTest` (`@SpringBootTest` + H2 PostgreSQL mode),
`LibraryEndpointsSecurityTest` (Layer 2 real filter chain: AC-6 auth + `SecurityConfig`
invariant + full-stack AC-3/AC-4/AC-5/AC-7). Expected Phase 1 RED: the test module fails at
`test-compile` on the unimplemented `com.streamvault.backend.library` symbols and
`TmdbGateway.movie`, consistent with the story-002/005/006 convention.

Cross-story amendment (documented, not silent): STORY-006's
`TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented`
pinned the migration set at V1..V4. It is updated here to `containsExactly(V1..V5)` with
`V5__create_library_movies_table.sql` named and the assertion message + class Javadoc reworded to
record that V5 is STORY-007's `library_movies` table. The persistence-token source scan in the
same class is unchanged and still green; no STORY-006 AC coverage is dropped.

Spec ambiguity surfaced to Brian (not resolved silently): AC-6 reads "can only add to, and see,
movies in their own library". STORY-009 owns the user-facing library read surface and viewing is
not in this story's Out of Scope. This contract implements the **add path only** and treats the
"see" half as an isolation guarantee (every write bound to `principal.userId()`, every repository
query user-scoped, tests prove one user's rows are invisible to another's). If Brian wants a
minimal `GET /api/library/movies` in this story, Test will add the endpoint contract and its
tests in a revision. Raised in the API contracts doc under "Spec clarification surfaced to Brian".

Phase 1 design review complete: Dev agreed on round 1
(`docs/specs/design/story-007-agreed.md`), no blocking concerns against AC-1..AC-7 or the
cross-story invariants. The design closely mirrors established precedent (story-005
`RatingType`/`parseRatingType`/`InvalidRatingTypeException` for the `WatchStatus` parse path,
story-006 `TmdbGateway`/`RestClientTmdbGateway`/`MockRestServiceServer` seam for `movie(long)`,
the `users` table conventions for `library_movies`, ADR-001 Layer 1/2 for the tests). Three
non-obvious implementation points recorded for Brian: (1) `movie(long)` needs a finer catch than
the shared `fetch(...)` helper - a TMDB 404 maps to `TmdbTitleNotFoundException` via a specific
`HttpClientErrorException.NotFound` catch while 401/403/429/5xx/transport stay
`TmdbUnavailableException`; it gets its own small private fetch rather than routing through the
`TmdbResultPage`-typed helper; (2) the unique-constraint race backstop relies on
`SimpleJpaRepository.save` flushing the `IDENTITY` insert within its own transaction so the
`DataIntegrityViolationException` surfaces from `save(...)` and is caught there; (3)
`added_at TIMESTAMP` mapped to `Instant` reuses the existing `User.createdAt` pattern verbatim,
no new schema convention. The AC-6 "and see" spec interpretation Test surfaced is noted as
pending Brian's decision but does not block the add path. Dev will add lower-level unit tests
below Test's integration boundary: exception message constants
(`InvalidWatchStatusExceptionTest` mirroring `InvalidRatingTypeExceptionTest`,
`DuplicateLibraryMovieException`, `TmdbTitleNotFoundException` id retention), the service
ordering nuance (invalid status rejected before the duplicate check), DTO-side response mapping
(`status` as `name()` string, null `releaseYear`/`posterUrl` pass-through), the `LibraryMovie`
entity shape (constructor sets `addedAt`, getters only), and a `RestClientTmdbGatewayMovieTest`
403 case plus an empty-body case.

Next: Dev implementation - unit tests first, then make Test's ten failing classes plus the new
Dev unit tests pass under `mvn clean verify`.

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
- [ ] STORY-006: TMDB Search and Browse (`docs/specs/story-006-tmdb-search-browse.md`) - Phase 3 complete: Test verified PR #26, full suite green 147/147 via `mvn clean verify`, all AC-1..AC-8 + invariants covered, regression analysis clean, **APPROVED**; awaiting Brian's review and merge
- [ ] STORY-007: Add Movie from TMDB to Library (`docs/specs/story-007-add-movie-from-tmdb.md`) - prereq STORY-006 - Phase 3 complete: Test pulled `feature/story-007-add-movie-from-tmdb`, ran `mvn clean verify` (225/225 green, 0 failures/errors, JaCoCo 75% gate met), ran regression analysis on the Dev diff (three additive shared-infra touch points - `GlobalExceptionHandler` mappings, `TmdbGateway.movie(long)`, `V5` migration - all covered by pre-existing suite re-running green plus Phase 1 tests; no new regression tests required), reviewed and accepted Dev's `@Transactional` test-isolation fix, confirmed every AC-1..AC-7 and every invariant is covered by a passing test. **APPROVED** on PR #28 via `gh pr review --approve`; AC-6 "and see" read surface deferred to STORY-009 per the agreed contract - Brian's call at merge. Awaiting Brian's review and merge
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
