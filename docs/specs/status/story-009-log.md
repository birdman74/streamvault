# STORY-009 Log: View and Filter My Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-009-view-filter-library.md`
Branch: `feature/story-009-view-filter-library`
PR: none yet

---

## Phase 1 Design Review: Test round 2 revision (2026-11-06)

Responds to `docs/specs/design/story-009-dev-feedback-r2.md`. Full detail in
`docs/specs/design/story-009-test-revision-r2.md`. Concern 1 accepted as technically valid - verified
independently by reading `LibrarySeries.java`/`LibrarySeason.java` before accepting Dev's claim,
rather than taking it on faith; both `seasons` and `episodes` are confirmed `List`-typed bags. No AC
or invariant coverage dropped.

**Fix adopted** (Dev's suggestion, one correction): split the single two-level `@EntityGraph` into
two queries sharing one session.
- `LibrarySeriesRepository.findByUserId` now carries `@EntityGraph(attributePaths = {"seasons"})`
  only (single bag, safe).
- New repository `LibrarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn(seriesIds)` -
  `@Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH s.episodes WHERE
  s.librarySeries.id IN :seriesIds")`, called once per `list(...)` call only when `findByUserId`
  returned at least one series. Correction to Dev's sketch: the real field on `LibrarySeason`
  referencing its parent is `librarySeries`, not `series` (`LibrarySeason.java:35`).
- `LibraryService`'s constructor gains `LibrarySeasonRepository` as a third dependency; algorithm
  step 5 rewritten to call both repositories, relying on `@Transactional(readOnly = true)` (unchanged
  from round 1) to make both calls join one session under Spring's default `REQUIRED` propagation.
  Still 2 queries total for the series branch per call, regardless of library size.

**Test changes**: the single eager-fetch invariant row/test is split into three, matching the two-
query design - `LibrarySeriesRepositoryEagerFetchTest` retargeted to assert only `seasons` (method
renamed `should_haveSeasonsAlreadyInitialized_when_accessedAfterFindByUserIdReturns`); new
`LibrarySeasonRepositoryEagerFetchTest` for the episode fetch in isolation; new
`LibraryServiceEagerFetchIntegrationTest`, autowiring the real `LibraryService` (not Mockito-mocked),
proving the two repository calls actually share one session when made from inside `list(...)` -
asserts the specific rolled-up status (`CURRENTLY_WATCHING` from a seeded mix of `WATCHED` and
`PLANNED` seasons) rather than only "no exception," since a partially-initialized collection would
silently roll up to the wrong status instead of throwing.

Ran `mvn test-compile` against the real story-007/008 production code: all three eager-fetch test
files fail only on "cannot find symbol" for not-yet-implemented production code
(`LibrarySeasonRepository`, `LibraryService`, `LibraryListResponse`, `findByUserId`) - same expected
Phase 1 RED state as the rest of the story, no unrelated compile error. Awaiting Dev's response to
this revision.

---

## Phase 1 Design Review: Dev round 2 (2026-11-06)

Responds to `docs/specs/design/story-009-test-revision-r1.md`. Full detail in
`docs/specs/design/story-009-dev-feedback-r2.md`. Needs revision, not agreement - but the gap is
narrow: the `@Transactional(readOnly = true)` half of round 1's fix is fine; only the specific
`@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})` is the problem.

Checked `LibrarySeries.seasons` and `LibrarySeason.episodes` directly in source rather than taking
the contract's entity graph on faith: both are `List`-typed `@OneToMany` collections (Hibernate
bags), not `Set`s. Requesting two nested `*ToMany` bag paths in one `@EntityGraph` is the known
Hibernate `MultipleBagFetchException` trigger - it will throw the first time `findByUserId` executes
against a series with at least one season, which is exactly the case Test's own new
`LibrarySeriesRepositoryEagerFetchTest` is written to exercise. Same blast radius as the original
round 1 bug (every request that includes a series), just a different exception at the same call
site.

Suggested fix that needs no entity class change (keeping the entities genuinely read-only per this
story's own cross-story invariant): split into two fetches inside the same `@Transactional`
service call - `@EntityGraph(attributePaths = {"seasons"})` alone on `findByUserId` (single bag,
safe), plus a second query loading every episode for every season just fetched in one round trip
(`LEFT JOIN FETCH ... WHERE season.series.id IN :seriesIds`), attaching onto the already-managed
season entities within the same session. Still 2 queries total per call regardless of library size.
Open to Test's own alternative (e.g. `Set`, `@BatchSize`, `FetchMode.SUBSELECT`) as long as it
doesn't touch the entity classes - only objecting to the specific entity graph as written, since it
won't run.

No AC or invariant coverage disputed; everything else from round 1's revision remains agreed.

---

## Phase 1 Design Review: Test round 1 revision (2026-11-06)

Responds to `docs/specs/design/story-009-dev-feedback-r1.md`. Full detail in
`docs/specs/design/story-009-test-revision-r1.md`. Both of Dev's concerns accepted as technically
valid; the binding question answered experimentally. No AC or invariant coverage dropped.

1. **Concern 1/2 (LazyInitializationException + N+1), accepted and resolved together**: new design
   decision 8 in the api-contracts doc - `LibrarySeriesRepository.findByUserId` now carries
   `@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})`, and `LibraryService.list(...)`
   is `@Transactional(readOnly = true)`, exactly the fix Dev suggested. New cross-story invariant
   added to the test plan, with a new dedicated test file,
   `LibrarySeriesRepositoryEagerFetchTest`, deliberately carrying no `@Transactional` so it exercises
   the real session-closing behavior under `spring.jpa.open-in-view: false` instead of the existing
   `LibrarySeriesRepositoryTest`'s class-level `@Transactional`, which would mask the bug. Verified
   the new file compiles against the real, already-implemented story-007/008 entities and fails
   only on the expected not-yet-existing `findByUserId` symbol - the same RED state as the rest of
   the story.
2. **Question 1 (repeated query param -> `List<Enum>` record binding), confirmed experimentally**:
   built a throwaway `@WebMvcTest` spike against this project's actual Spring Boot 3.5.16 / Spring
   Framework 6.2.19 (no production code touched, deleted before this revision was committed) and
   confirmed both that the binding works and that an unrecognized element still routes through
   `MethodArgumentNotValidException` rather than bypassing the existing 400 handler. No test or
   contract change was needed as a result; this is recorded in a new "Response to Dev's round 1
   Question 1" section of the api-contracts doc for Dev's visibility.

No pushback from Test on anything in Dev's feedback - both concerns were correct reads of this
codebase's actual JPA configuration, not theoretical. Awaiting Dev's response to this revision.

---

## Phase 1 Design Review: Dev round 1 (2026-11-06)

Dev reviewed `story-009-test-plan.md` and `story-009-api-contracts.md` against the PO story and
against this codebase's actual source (not just the contract's prose). Verdict: needs revision,
`docs/specs/design/story-009-dev-feedback-r1.md` committed. The merge/filter/sort/paginate algorithm,
pagination design, response shape, and `WatchStatusRollup` derivation rule are agreed as specified -
no objection to the overall design.

Two concerns raised, both about runtime correctness under this project's actual JPA configuration
rather than the algorithm itself:

1. **`WatchStatusRollup.seriesStatus(series)` will throw `LazyInitializationException` as
   contracted.** `backend/src/main/resources/application.yml` sets `spring.jpa.open-in-view: false`;
   `LibrarySeries.seasons` and `LibrarySeason.episodes` are both default-`LAZY`. Every existing read
   of `getSeasons()`/`getEpisodes()` in the codebase today (`LibrarySeriesService`) only ever walks
   entities just built in Java memory within the same add-series call, never a `LibrarySeries`
   freshly loaded from the database - STORY-009's `findByUserId` is the first time this codebase
   loads a series from the DB and then walks into its seasons/episodes afterward. With no
   `@Transactional` and no eager-fetch strategy specified on the new finder, the session closes
   before `WatchStatusRollup.seriesStatus` runs, so this throws on every request that includes a
   series - the normal case, not an edge case. Dev asked Test to pick an explicit fix (e.g.
   `@Transactional(readOnly = true)` on `LibraryService.list` plus an `@EntityGraph` on
   `LibrarySeriesRepository.findByUserId` covering `seasons` and `seasons.episodes`) and state it in
   the contract.
2. **Once fixed, the fetch strategy should also avoid N+1 per series on every request**, since
   design decision 3 requires every series' rollup computed on every call (for filtering), not just
   the returned page. Suggested folding this into the same `@EntityGraph` fix as concern 1.

One non-blocking question: whether repeated query params binding into a `List<Enum>` record
constructor parameter (`?type=MOVIE&type=SERIES` -> `LibraryListRequest.type`) has precedent in this
codebase. Checked `TmdbBrowseRequest`/`TmdbSearchRequest`, cited by the contract as precedent for the
unrecognized-value-is-400 behavior - both only bind a single scalar value, not a repeated/list-valued
one. Asked Test to confirm this binds as expected and still routes a bad element through the existing
`MethodArgumentNotValidException` handler before Dev relies on it.

No pushback on PO acceptance criteria, `SecurityConfig`, ADR-001 compliance, or the
`GlobalExceptionHandler` non-change - all agreed as specified.

---

## Phase 1: Test goes first

Complete on branch `feature/story-009-view-filter-library`, cut from `main` after STORY-008
merged (PR #29, commit `4ee0dc5`). Test plan (`docs/specs/design/story-009-test-plan.md`) maps every
AC-1..AC-8 plus the cross-story invariants to named tests; API contracts
(`docs/specs/design/story-009-api-contracts.md`) define the first read path over the library,
`GET /api/library`, which merges `library_movies` (STORY-007) and `library_series` (STORY-008) into
one bounded, paginated, filterable list for the authenticated user. This story adds no table and no
Flyway migration - purely additive read-only code over the existing schema. No `SecurityConfig`
change: `/api/library` already falls under the existing `anyRequest().authenticated()` rule.

Seven design decisions recorded as Test's implementation calls rather than spec ambiguities (see
the API contracts doc): (1) default sort is `addedAt` descending, tie-broken by `type` ascending
then `id` ascending, since a merged list from two independent primary-key sequences needs a fully
deterministic tie-break for stable pagination; (2) filtering/sorting/pagination happen in memory in
a new `LibraryService` over each user's own rows (`findByUserId` on each repository), not a
cross-table SQL `UNION` - a reasonable trade at personal-library scale; (3) a series' `status` for
display and filtering is always the `WatchStatusRollup`-derived value, never a stored column, since
`library_series`/`library_seasons` carry no status column by story-008's own design; (4) pagination
is `page`/`pageSize` query params defaulting to `1`/`20`, `pageSize` capped at `100`, mirroring the
existing `TmdbSearchRequest`/`TmdbBrowseRequest` nullable-Integer-with-service-side-default
convention; (5) a `page` beyond the last page returns `200` with an empty list, the same treatment
as a no-match filter; (6) `year` is the unified response field name for a movie's `releaseYear` or a
series' `firstAirYear`; (7) `id` is unique only combined with `type`, since `library_movies.id` and
`library_series.id` are independent sequences that can collide.

Nine failing test files committed: `WatchStatusRollupTest` (plain JUnit, pins the exact STORY-012
AC-2/AC-3 derivation rule - Watched when every child is Watched, Planned when every child is
Planned, Currently Watching otherwise, empty grouping treated as Planned - including the two-level
series-from-seasons-from-episodes case), `dto/LibraryListRequestValidationTest` (plain Jakarta
Validator, pins `@Min(1)` on `page` and `@Min(1)/@Max(100)` on `pageSize`, both optional), `LibraryServiceTest`
(Mockito, mocks both repositories; covers every AC-1 through AC-8 permutation - combined list, type
filter including that the excluded repository is never even queried, status filter including the
any-of-several case, series filtered by rolled-up status never raw episode status, type+status AND
combination, pagination math and the beyond-last-page empty case, strict user-scoping, empty
library, empty filter match, and the full sort-order/tie-break rule; uses
`ReflectionTestUtils.setField` to control `addedAt`/`id` on fixtures since both entities are
getters-only with `addedAt` stamped internally at construction - a new technique in this test suite,
but a standard Spring Test utility, used only in test code), `LibraryControllerTest` and
`LibraryControllerPrincipalConventionTest` (`@WebMvcTest` slice per ADR-001, repeated `type=`/
`status=` query-param binding, 400 for an unrecognized enum value or out-of-bounds `page`/
`pageSize`), `LibraryListEndpointsSecurityTest` (Layer 2 real filter chain and H2, kept as its own
class rather than added to `LibraryEndpointsSecurityTest` so that file stays untouched - the AC-7
auth and two-user isolation home, plus full-stack AC-1/AC-3/AC-4/AC-5/AC-6/AC-8). `LibraryMovieRepositoryTest`
and `LibrarySeriesRepositoryTest` amended (new test methods, not new files) for the new
`findByUserId` finder on each repository - empty-result-without-throwing, round-trip-returns-all-of-
one-user's-rows, cross-user-exclusion - the cross-story repository invariant. Expected Phase 1 RED
confirmed by running `mvn test-compile`: the test module fails to compile on the unimplemented
`LibraryItemType`, `WatchStatusRollup`, `LibraryService`, `LibraryController`, `LibraryListRequest`,
`LibraryItemResponse`, `LibraryListResponse` symbols and the two new repository finder methods,
consistent with the story-002/005/006/007/008 convention. No Flyway-migration-pin amendment needed
this time (unlike story-007/008): this story adds no new table, so
`TmdbPackageReadOnlyConventionTest`'s `V1..V6` pin is untouched.

Spec ambiguity surfaced to Brian, not resolved silently: AC-2 requires the series status shown in
this list to be "the rolled-up status per STORY-012," and AC-4 requires filtering by that same
status, even though STORY-012 is not a listed prerequisite of STORY-009 and comes after it in the
epic's own story order. This contract implements only the roll-up *computation* described by
STORY-012's own AC-2/AC-3 text (`WatchStatusRollup`, a pure stateless derivation) as a read-only
display and filter value - no settable status, no bulk-update action, and no STORY-012 acceptance
criterion is claimed here. When STORY-012 is designed, its contract should reuse `WatchStatusRollup`
unchanged rather than re-deriving the same rule. Full detail in the API contracts doc under "Spec
clarification already surfaced to Brian."

This is also the story that resolves the "and see" half of STORY-007 AC-6 and STORY-008 AC-7 left
open on those stories' PRs: `GET /api/library` is the user-facing read surface those contracts said
STORY-009 would own. No further action needed beyond what already exists - the isolation guarantee
those stories already built (every write bound to `principal.userId()`) is exactly what this
endpoint's user-scoping relies on.

Design iteration with Dev has not started yet - this is the Phase 1 commit. Per the workflow, at
least one Dev review round is required before implementation begins.
