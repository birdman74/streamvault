# STORY-009 Log: View and Filter My Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-009-view-filter-library.md`
Branch: `feature/story-009-view-filter-library`
PR: #31

---

## Phase 3: final PR verification - APPROVED (2026-11-08)

**Verdict: APPROVED** on PR #31.

Pulled `feature/story-009-view-filter-library` (already up to date with origin, nothing new since
Dev's `aa3af00`). Ran `mvn clean verify` independently from a clean state: **385 tests, 0
failures, 0 errors, `BUILD SUCCESS`** across all 65 test classes, confirmed by aggregating every
`target/surefire-reports/*.txt`.

**AC coverage** - every AC-1 through AC-8 in `story-009-test-plan.md` maps to at least one passing
test; spot-checked the mapped method names actually exist in source (not stale from an earlier
round) for AC-5 (`LibraryServiceTest.should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested`,
`LibraryListEndpointsSecurityTest.should_applyTypeAndStatusFiltersTogether_endToEnd`) and confirmed
their surefire results (`LibraryServiceTest`: 21/21 pass, `LibraryListEndpointsSecurityTest`:
11/11 pass).

**Regression analysis** - diffed `main...feature/story-009-view-filter-library` for shared
infrastructure risk:
- `LibraryMovieRepository` / `LibrarySeriesRepository` gain new `findByUserId` finders only; no
  existing finder signature changed. New `LibrarySeasonRepository` is new, used only by this
  story's `LibraryService`. The cross-story invariant ("new finder methods return empty without
  throwing") is covered by the amended `LibraryMovieRepositoryTest` /
  `LibrarySeriesRepositoryTest` methods already on the branch.
- `LibraryController` is a brand-new controller/route, not a change to an existing one; grepped the
  full diff for `SecurityConfig`, migrations, and `GlobalExceptionHandler` - none touched. The
  `/api/library` auth boundary is proven by `LibraryListEndpointsSecurityTest`'s 401 cases and the
  explicit assertions that `/api/health` and `/api/auth/me` stay unaffected.
- No existing test file outside the `library` package changed, and no existing library test method
  was altered beyond the two repository files' additive amendments already accounted for in the
  test plan.

No gap found and no regression found, so no new test was needed this round.

---

## Phase 2 close-out: re-verified green suite, opened PR (2026-11-08)

Picked up from Test's Phase 3 fix (00abf7e) with the branch unblocked and no PR yet. Re-ran
`mvn clean verify` myself rather than trusting the prior log entry at face value: 385 tests, 0
failures, 0 errors, `BUILD SUCCESS`, including `LibraryServiceTest` (21), `WatchStatusRollupTest`
(11), and `StreamvaultBackendApplicationTests` (1, full Spring context boot).

Checked the two lower-level unit test commitments from `story-009-agreed.md`'s Test Coverage
Confirmation before deciding whether anything was still owed:
- `WatchStatusRollup` edge cases: `WatchStatusRollupTest` (Test's file, committed in 0e2037f)
  already pins empty-is-Planned, all-Watched, all-Planned, every mixed-pair/triple combination,
  single-element, the two-level series rollup, a series with no seasons, and a season with no
  episodes. No case from the contract's stated rule is unpinned; adding a parallel Dev-authored
  test here would just duplicate existing coverage.
- Constructor/DI wiring for the new three-repository `LibraryService`: `StreamvaultBackendApplicationTests`
  boots the full Spring context, which fails if `LibraryService`'s constructor cannot be satisfied
  by the real `LibraryMovieRepository`/`LibrarySeriesRepository`/`LibrarySeasonRepository` beans.
  That already exercises the exact wiring concern the agreed design flagged; a dedicated duplicate
  test would add no coverage a plain constructor-injected `@Service` needs.

No gap found, so no new test files added this round. Opening a PR (`feature/story-009-view-filter-library`
-> `main`) as `claude-streamvault-dev`; issue #30 (`needs-brian: story-009 Dev implementation stalled`)
was opened against the now-resolved blocker from the e021cf3 run and is left for the pipeline's own
blocked-label reconciliation rather than closed by hand here.

---

## Phase 3: fixed the AC-5 test contradiction, full suite green, no PR yet (2026-11-08)

Fixes the self-contradiction Dev's previous entry (below) found between two of Test's own committed
tests in `LibraryServiceTest.java`. Dev's analysis was correct and is not revisited here: the
contract's algorithm step 5 says skip the series repository entirely for a MOVIE-only type filter,
`should_notQueryTheSeriesRepositoryAtAll_when_typeFilterIsMovieOnly` pins exactly that, and
`should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested` stubbed
`librarySeriesRepository.findByUserId` for the identical MOVIE-only filter shape, so Mockito's
default strict stubbing failed that stub as unused no matter which of the two mutually exclusive
behaviors the implementation chose. This was Test's test defect, not an implementation defect, and
not a contract ambiguity - confirmed independently before editing anything.

**Fix**: removed the unused `librarySeriesRepository` stub from
`should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested`. The test still
proves the AND semantics AC-5 requires, now entirely within the movie branch: it stubs
`libraryMovieRepository.findByUserId` with two movies, one `WATCHED` and one `PLANNED`, requests
`type=MOVIE` AND `status=WATCHED`, and asserts exactly one item comes back (the watched movie) -
the planned movie proves the status half of the AND is still enforced even though both movies match
the type half. No series fixture or stub is needed to prove an AND condition between two filters
that both apply within the movie branch; the series-repository-is-skipped behavior already has its
own dedicated pin in `should_notQueryTheSeriesRepositoryAtAll_when_typeFilterIsMovieOnly`, so nothing
about this change drops coverage.

No AC or invariant coverage changed: AC-5 ("type and status combine with AND semantics") is still
pinned by this same test, now without appealing to a scenario the contract's own algorithm forbids.

**Full suite re-run**: `mvn clean verify` from `backend/` - all 65 test classes, 0 failures, 0
errors (previously 1 error, the fixed test above). Full pass confirmed via every
`target/surefire-reports/*.txt` file showing `Failures: 0, Errors: 0`.

**Reviewed every other story-009 test file against the agreed contract and design decisions**
(`story-009-api-contracts.md`, `story-009-agreed.md`) for the same class of defect - a stub or
assertion that contradicts the algorithm, a signature mismatch, or an assertion pinning behavior the
contract does not require:
- `LibraryServiceTest.java` (remaining 23 tests) - all consistent with the algorithm's 10 numbered
  steps, the 3-arg constructor, and the `lenient()` default stub for `librarySeasonRepository`
  already reconciled in the prior log entry below.
- `LibraryControllerTest.java`, `LibraryControllerPrincipalConventionTest.java` - request binding,
  status codes, and `principal.userId()` threading all match the contract's `LibraryController`
  listing exactly.
- `LibraryListEndpointsSecurityTest.java` - full-stack 401/200 flows, two-user isolation, and the
  unaffected `/api/health` / `/api/auth/me` checks all match AC-7 and the existing `SecurityConfig`
  boundary; no widening of `permitAll()` asserted or implied.
- `dto/LibraryListRequestValidationTest.java` - `@Min(1)` / `@Min(1) @Max(100)` bounds and the
  "type/status carry no Bean Validation constraint" assumption both match the contract's
  `LibraryListRequest` record exactly.
- `LibrarySeriesRepositoryEagerFetchTest.java`, `LibrarySeasonRepositoryEagerFetchTest.java`,
  `LibraryServiceEagerFetchIntegrationTest.java` - the single-bag `@EntityGraph` on
  `findByUserId`, the standalone `findSeasonsWithEpisodesByLibrarySeriesIdIn` fetch, and the
  session-sharing proof all match design decision 8 (round 2) with no residual round-1 two-level
  `@EntityGraph` assumption anywhere.
- `WatchStatusRollupTest.java` - every case (empty-is-Planned, all-Watched, all-Planned, any mix,
  empty season within a series) matches the contract's stated rule with no deviation.
- `LibraryMovieRepositoryTest.java`, `LibrarySeriesRepositoryTest.java` - the `findByUserId`
  additions (added for this story on top of each file's pre-existing story-007/008 coverage) match
  the "no match is `List.of()`, not an exception" invariant and correctly scope to one user.

No other contradiction found. No genuine implementation bug found either - the full suite's clean
pass means every AC-1 through AC-8 and every invariant this story owns already has a passing test,
so there is nothing to list here as a defect left for Dev.

Not opening a PR or posting a verdict comment from this run: no PR exists yet for this branch (`gh
pr list --head feature/story-009-view-filter-library` returns empty), and Phase 3's PR-verdict
hand-off is specifically for when Dev opens a PR or pushes a fix to one already open. That remains
Dev's next move now that the test-side blocker is gone.

---

## Phase 2 Implementation: blocked by a self-contradiction in LibraryServiceTest (2026-11-08)

Implemented every production class per `story-009-agreed.md`'s Implementation Plan:
`LibraryItemType`, `WatchStatusRollup`, `LibraryMovieRepository.findByUserId`,
`LibrarySeriesRepository.findByUserId` (`@EntityGraph(attributePaths = {"seasons"})`),
`LibrarySeasonRepository` (new, `findSeasonsWithEpisodesByLibrarySeriesIdIn`), the three new DTOs
(`LibraryListRequest`, `LibraryItemResponse`, `LibraryListResponse`), `LibraryService` (merge/
filter/sort/paginate algorithm exactly per the contract's numbered steps, `@Transactional(readOnly
= true)`), and `LibraryController` (`GET /api/library`, `principal.userId()` as the only source of
the owning user id).

`mvn compile` and `mvn test-compile` both pass clean - every test file Test committed compiles
against the implementation with no signature mismatch, confirming the Phase 1 design-review
conformance check held.

`mvn test` on the full suite: 198 tests run, 0 failures, 1 error -
`LibraryServiceTest.should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested`
fails with `org.mockito.exceptions.misusing.UnnecessaryStubbingException` pointing at its own
`when(librarySeriesRepository.findByUserId(USER_ID))...` stub (line 267).

Root cause is a self-contradiction between two of Test's own committed tests in the same file,
not an implementation defect - verified by checking that no alternative implementation choice
satisfies both:

- `should_notQueryTheSeriesRepositoryAtAll_when_typeFilterIsMovieOnly` (line 143) calls
  `libraryService.list(USER_ID, List.of(LibraryItemType.MOVIE), null, null, null)` and asserts
  `verifyNoInteractions(librarySeriesRepository)` - the series repository must receive zero calls
  whenever the type filter is MOVIE-only. This matches the contract's algorithm step 5 ("Otherwise
  (SERIES not in the effective type set) skip the whole series branch") exactly, and is what the
  implementation does.
- `should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested` (line 263-275)
  calls `libraryService.list(USER_ID, List.of(LibraryItemType.MOVIE), List.of(WatchStatus.WATCHED),
  null, null)` - the identical MOVIE-only type filter - but stubs
  `librarySeriesRepository.findByUserId(USER_ID)` beforehand. `@ExtendWith(MockitoExtension.class)`
  defaults to strict stubs with no class-level `@MockitoSettings(strictness = LENIENT)` and no
  per-stub `lenient()` on this particular stub, so Mockito requires every stub to be used by that
  test or fails it.

An implementation that skips the series repository call for a MOVIE-only filter (the contract's
own stated algorithm, and what the first test above pins) makes the second test's stub unused ->
`UnnecessaryStubbingException`. An implementation that always calls both repositories and filters
by type afterward instead would satisfy the second test but fail the first test's
`verifyNoInteractions` assertion. No third implementation satisfies both simultaneously - this is
not a design or contract problem (the contract's algorithm is unambiguous and the current
implementation follows it exactly), it is Test's test file asserting two mutually exclusive
behaviors for the same input shape.

Per Dev's CLAUDE.md Phase 2 instructions, stopping here rather than editing `LibraryServiceTest.java`
or adding implementation workarounds. Implementation is committed on this branch in its current
(contract-correct) form; no PR opened. Ending this run with a `NEEDS BRIAN` message.

---

## Test reconciliation gap: LibraryServiceTest not amended for round 2 constructor (2026-11-08)

Brian flagged that `LibraryServiceTest.java` was never amended after round 2 changed
`LibraryService`'s constructor from 2 arguments (`LibraryMovieRepository`,
`LibrarySeriesRepository`) to 3 (adding `LibrarySeasonRepository`, per
`story-009-test-revision-r2.md`'s Test Code Reconciliation table and
`story-009-api-contracts.md`'s contract). Line 56 of the committed test still called the old
2-argument constructor - a gap in round 2's reconciliation that should have been caught then and
was not.

Fixed: added a `@Mock LibrarySeasonRepository librarySeasonRepository` field; `setUp()` now
constructs `LibraryService` with all three repositories, matching the contract exactly. Added one
`lenient()` default stub (`findSeasonsWithEpisodesByLibrarySeriesIdIn(anyList()) -> List.of()`) in
`setUp()` rather than per-test: this mock exists only so the round 2 contract's session-sharing side
effect (attaching fetched episodes onto already-loaded `LibrarySeason` entities by identity within
one Hibernate session) has something to call into. A plain Mockito unit test has no real session for
that side effect to act on, and no assertion in this file depends on what the mock returns - every
`series(...)` fixture already carries its seasons/episodes fully populated in memory via `addSeason`
/ `addEpisode`, which is what `WatchStatusRollup.seriesStatus(...)` actually reads. The real
session-sharing guarantee remains covered separately by `LibraryServiceEagerFetchIntegrationTest`
against a real Spring context - unaffected by this fix.

No other test in this file changed: none of them asserted on `LibrarySeasonRepository` and none
needed to, since the contract's `LibraryService.list(...)` algorithm only calls
`findSeasonsWithEpisodesByLibrarySeriesIdIn` for its session-sharing side effect, not for any value
these fixtures' in-memory entities don't already carry.

Updated `story-009-test-plan.md`'s "Service unit tests" bullet to describe the three-repository
mock setup and the lenient default stub, so the plan matches the test file as committed.

Verified: ran `mvn compile test-compile` from `backend/`. Confirmed the only compile errors are the
expected Phase 1 RED state - `LibraryService`, `LibraryItemType`, `WatchStatusRollup`,
`LibrarySeasonRepository`, and the DTOs do not exist in `src/main` yet (checked directly;
`LibraryService.java` is absent from `src/main/java/com/streamvault/backend/library`) - and that no
new or different error is attributable to this file. No production code touched.

---

## Phase 1 Design Review: Dev round 3 - agreed (2026-11-06)

Responds to `docs/specs/design/story-009-test-revision-r2.md`. Full detail in
`docs/specs/design/story-009-agreed.md`. Design accepted; implementation can begin.

Before agreeing, Dev independently re-checked the one gap neither round 1 nor round 2's test
description explicitly closed: whether `LibrarySeriesRepository.findByUserId`'s
`@EntityGraph(attributePaths = {"seasons"})` could return the same `LibrarySeries` row duplicated
once per season - the classic to-many-fetch Cartesian product problem, and exactly why round 2's own
`LibrarySeasonRepository` query needed an explicit `DISTINCT`. Verified against how Spring Data JPA
actually executes `@EntityGraph`-annotated derived finder methods (a Criteria-API path that applies
root-entity de-duplication automatically) versus a hand-written `@Query` with `JOIN FETCH` (which
does not de-duplicate without an explicit `DISTINCT` keyword, which round 2's `LibrarySeasonRepository`
query already has). No defect: the two queries are different cases and both are already handled
correctly as specified. No new concern raised as a result.

Also re-verified, directly against committed source rather than the contract's prose alone, that
nothing else in the round 2 design has drifted from this codebase's actual state: `LibrarySeries`,
`LibrarySeason`, `LibraryMovie`, `WatchStatus` field/enum shapes; `AuthenticatedUser(Long userId,
String email)`; `SecurityConfig`'s `permitAll()` boundary and catch-all `anyRequest().authenticated()`;
`GlobalExceptionHandler`'s existing `MethodArgumentNotValidException` mapping (no new mapping needed);
and that `LibraryMovieRepository`/`LibrarySeriesRepository` do not yet have `findByUserId` (purely
additive, as the contract claims). All match. No gap found between the already-committed Phase 1 test
files (`LibrarySeriesRepositoryEagerFetchTest`, `LibraryServiceEagerFetchIntegrationTest`, and the
rest) and the contract they test against.

Rounds 1 and 2 already extracted the two substantive runtime-correctness defects this design had
(`LazyInitializationException` under `open-in-view: false`, then `MultipleBagFetchException` from the
first fix's two-level entity graph); both are resolved by the round 2 two-query split. No further
concerns. `story-009-agreed.md` committed; implementation starts next.

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
