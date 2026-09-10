# STORY-007 Log: Add Movie from TMDB to Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-007-add-movie-from-tmdb.md`
Branch: `feature/story-007-add-movie-from-tmdb`
PR: #28

---

## Phase 3: Test final PR verification

Complete on branch `feature/story-007-add-movie-from-tmdb`, PR #28 against `main` -
**APPROVED**. Pulled the branch, ran `mvn clean verify`: full suite green **225/225**, 0 failures,
0 errors, JaCoCo 75% instruction gate met ("All coverage checks have been met"). Every AC-1..AC-7
and every cross-story invariant is covered by at least one passing test.

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
`LibraryMoviesTableConstraintsTest`) reviewed and accepted: it fixes a real test-isolation defect in
the Phase 1 tests (fixed-email `users` seed in `@BeforeEach` collided across methods sharing one
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
Brian in Phase 1 and agreed in `story-007-agreed.md`; called out again here so Brian makes the final
call at merge.

**Verdict: Test APPROVED on PR #28** - verdict posted as the Phase 3 Test Run Summary comment on
PR #28 (`gh pr review --approve` is rejected by GitHub because `gh` authenticates as the PR author
account `briankcampbell-streamvault-bot`: "Can not approve your own pull request"). Awaiting
Brian's review and merge.

---

## Phase 2: Dev implementation

New `com.streamvault.backend.library` package: `WatchStatus` (`PLANNED` / `CURRENTLY_WATCHING` /
`WATCHED`, plain enum), `LibraryMovie` `@Entity` -> `library_movies` (`IDENTITY` id, `user_id`
NOT NULL, `@Enumerated(STRING)` status, `added_at` mapped to `Instant` exactly as `User.createdAt`;
public all-args constructor stamps `addedAt`, `protected` no-arg for JPA, getters only),
`LibraryMovieRepository` (`existsByUserIdAndTmdbId` + `findByUserIdAndTmdbId`, every access
user-scoped), `dto/AddMovieRequest` (`@NotNull @Positive Long tmdbId`, unconstrained `String
status`), `dto/LibraryMovieResponse` (`status` as `WatchStatus.name()`), `exception/`
`InvalidWatchStatusException` (message from `WatchStatus.values()`, mirrors
`InvalidRatingTypeException`) and `DuplicateLibraryMovieException` (fixed message),
`LibraryMovieService` (constructor `(LibraryMovieRepository, TmdbGateway)`; `addMovie` runs the
agreed five-step order - parse status, duplicate pre-check before TMDB, `tmdbGateway.movie`, `save`
with `DataIntegrityViolationException` -> `DuplicateLibraryMovieException` race backstop, map saved
row), `LibraryMovieController` (`POST /api/library/movies`, owning id always
`principal.userId()`).

TMDB package extended (still persistence-free): `dto/TmdbMovie`, `exception/`
`TmdbTitleNotFoundException` (persistence-free, retains `tmdbId`), `TmdbGateway.movie(long)`, and
`RestClientTmdbGateway.movie(long)` - its own small fetch (not the `TmdbResponse`-typed shared
`fetch` helper) with a two-branch catch: `HttpClientErrorException.NotFound` ->
`TmdbTitleNotFoundException`, every other `RestClientException` / non-2xx ->
`TmdbUnavailableException`; reuses `parseYear` / `imageBaseUrl`; `search` / `browse` byte-for-byte
unchanged. `GlobalExceptionHandler` gains three additive `@ExceptionHandler` methods (400
`InvalidWatchStatusException`, 409 `DuplicateLibraryMovieException`, 404
`TmdbTitleNotFoundException`); no existing mapping altered, `TmdbUnavailableException` -> 502
reused unchanged. `V5__create_library_movies_table.sql` added exactly as contracted (`BIGSERIAL` id,
`user_id BIGINT NOT NULL REFERENCES users (id)`, `status VARCHAR(30) NOT NULL`, `added_at TIMESTAMP
NOT NULL DEFAULT now()`, `CONSTRAINT uq_library_movies_user_tmdb UNIQUE (user_id, tmdb_id)`). No
`SecurityConfig`, `application.yml`, or `.env.example` change.

Dev lower-level unit tests added below Test's integration boundary:
`InvalidWatchStatusExceptionTest`, `DuplicateLibraryMovieExceptionTest`,
`TmdbTitleNotFoundExceptionTest` (message constants + `tmdbId` retention), `LibraryMovieTest`
(entity: `addedAt` stamped, `id` null pre-persist, `protected` no-arg ctor, getters only),
`LibraryMovieServiceOrderingTest` (unsupported status rejected before the duplicate check with
`verifyNoInteractions`; response built from the saved entity carrying `id` / `addedAt` / `status`
name), `RestClientTmdbGatewayMovieEdgeCasesTest` (403 stays unavailable; empty `{}` body -> null
fields, no NPE).

Deviation from the agreed design (test-mechanics only, flagged for Test's Phase 3 review): the two
Test-authored `@SpringBootTest` classes `LibraryMovieRepositoryTest` and
`LibraryMoviesTableConstraintsTest` seed fixed-email `users` rows in `@BeforeEach` with no rollback.
The class shares one cached context and one in-memory H2 database across methods, so from the
second method on the seed collided on `users.email` (`DataIntegrityViolationException` in `setUp` /
`seedUsers`), failing 8 of their combined 10 methods regardless of production code. Fix applied:
`@Transactional` on both classes (Spring's standard per-method auto-rollback) plus a Javadoc line
explaining why. No assertion, datasource, or test intent changed; the schema-level violations under
test are raised synchronously by H2 at statement execution so the surrounding rollback does not
mask them. Both classes green in isolation (4/4 and 6/6) and in the full run.

Spec interpretation unchanged from Phase 1: AC-6 "and see" is implemented as an isolation guarantee
(add path only; STORY-009 owns the read surface) pending Brian's call.

---

## Phase 1: Test goes first

Complete on branch `feature/story-007-add-movie-from-tmdb`, cut from an up-to-date `main`
(STORY-006 merged, PR #26, commit 33fb2a1). Test plan (`docs/specs/design/story-007-test-plan.md`)
maps every AC-1..AC-7 plus the cross-story invariants to named tests; API contracts
(`docs/specs/design/story-007-api-contracts.md`) define `POST /api/library/movies`
(`AddMovieRequest` -> `LibraryMovieResponse`), the shared `WatchStatus` enum (`PLANNED` default /
`CURRENTLY_WATCHING` / `WATCHED`), the `library_movies` table + `V5__create_library_movies_table.sql`
migration (`UNIQUE (user_id, tmdb_id)`, `user_id` NOT NULL FK to `users`), a new
`TmdbGateway.movie(long)` returning `TmdbMovie` with a `TmdbTitleNotFoundException` on a TMDB 404,
and three additive `GlobalExceptionHandler` mappings (400 invalid status, 409 duplicate, 404 not
found). No `SecurityConfig` change: `/api/library/**` falls under the existing
`anyRequest().authenticated()` rule.

Ten failing test classes committed: `dto/AddMovieRequestValidationTest`, `WatchStatusTest`,
`LibraryMovieServiceTest` (Mockito, the add algorithm), `RestClientTmdbGatewayMovieTest`
(MockRestServiceServer, `GET /movie/{id}` mapping + the 404-vs-outage split),
`LibraryMovieControllerTest` and `LibraryMovieControllerPrincipalConventionTest` (`@WebMvcTest`
slice per ADR-001), `LibraryMoviesTableConstraintsTest` and `LibraryMovieRepositoryTest`
(`@SpringBootTest` + H2 PostgreSQL mode), `LibraryEndpointsSecurityTest` (Layer 2 real filter
chain: AC-6 auth + `SecurityConfig` invariant + full-stack AC-3/AC-4/AC-5/AC-7). Expected Phase 1
RED: the test module fails at `test-compile` on the unimplemented
`com.streamvault.backend.library` symbols and `TmdbGateway.movie`, consistent with the
story-002/005/006 convention.

Cross-story amendment (documented, not silent): STORY-006's
`TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented`
pinned the migration set at V1..V4. It is updated here to `containsExactly(V1..V5)` with
`V5__create_library_movies_table.sql` named and the assertion message + class Javadoc reworded to
record that V5 is STORY-007's `library_movies` table. The persistence-token source scan in the same
class is unchanged and still green; no STORY-006 AC coverage is dropped.

Spec ambiguity surfaced to Brian (not resolved silently): AC-6 reads "can only add to, and see,
movies in their own library". STORY-009 owns the user-facing library read surface and viewing is
not in this story's Out of Scope. This contract implements the **add path only** and treats the
"see" half as an isolation guarantee (every write bound to `principal.userId()`, every repository
query user-scoped, tests prove one user's rows are invisible to another's). If Brian wants a
minimal `GET /api/library/movies` in this story, Test will add the endpoint contract and its tests
in a revision. Raised in the API contracts doc under "Spec clarification surfaced to Brian".

Phase 1 design review complete: Dev agreed on round 1 (`docs/specs/design/story-007-agreed.md`), no
blocking concerns against AC-1..AC-7 or the cross-story invariants. The design closely mirrors
established precedent (story-005 `RatingType`/`parseRatingType`/`InvalidRatingTypeException` for the
`WatchStatus` parse path, story-006 `TmdbGateway`/`RestClientTmdbGateway`/`MockRestServiceServer`
seam for `movie(long)`, the `users` table conventions for `library_movies`, ADR-001 Layer 1/2 for
the tests). Three non-obvious implementation points recorded for Brian: (1) `movie(long)` needs a
finer catch than the shared `fetch(...)` helper - a TMDB 404 maps to `TmdbTitleNotFoundException`
via a specific `HttpClientErrorException.NotFound` catch while 401/403/429/5xx/transport stay
`TmdbUnavailableException`; it gets its own small private fetch rather than routing through the
`TmdbResultPage`-typed helper; (2) the unique-constraint race backstop relies on
`SimpleJpaRepository.save` flushing the `IDENTITY` insert within its own transaction so the
`DataIntegrityViolationException` surfaces from `save(...)` and is caught there; (3)
`added_at TIMESTAMP` mapped to `Instant` reuses the existing `User.createdAt` pattern verbatim, no
new schema convention. The AC-6 "and see" spec interpretation Test surfaced is noted as pending
Brian's decision but does not block the add path. Dev will add lower-level unit tests below Test's
integration boundary: exception message constants (`InvalidWatchStatusExceptionTest` mirroring
`InvalidRatingTypeExceptionTest`, `DuplicateLibraryMovieException`, `TmdbTitleNotFoundException` id
retention), the service ordering nuance (invalid status rejected before the duplicate check),
DTO-side response mapping (`status` as `name()` string, null `releaseYear`/`posterUrl`
pass-through), the `LibraryMovie` entity shape (constructor sets `addedAt`, getters only), and a
`RestClientTmdbGatewayMovieTest` 403 case plus an empty-body case.