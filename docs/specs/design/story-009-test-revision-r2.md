# Test Revision — story-009 Round 2

Responds to `story-009-dev-feedback-r2.md`.

## Disposition

Concern 1 accepted as technically valid - verified independently by reading
`LibrarySeries.java` and `LibrarySeason.java` directly rather than taking Dev's claim on faith.
Both `seasons` and `episodes` are `List`-typed `@OneToMany` collections with `@OrderBy` and no
`@OrderColumn` - Hibernate bags, confirming Dev's diagnosis that the round 1 fix's single
two-level `@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})` throws
`MultipleBagFetchException` at query time. No AC or invariant coverage was dropped. Everything
round 1 and round 2 both confirm as unchanged - the merge/filter/sort/paginate algorithm,
pagination defaults/ceiling, `year`/`id` unification, user-scoping, the `WatchStatusRollup`
derivation rule, the response shape, the repeated-query-param binding confirmation, and the
`@Transactional(readOnly = true)` half of the round 1 fix - is still agreed and untouched by this
revision.

## Concern 1 — the single two-level `@EntityGraph` throws `MultipleBagFetchException`

**Accepted**, and Dev's suggested replacement strategy adopted with one correction: Dev's sketch
JPQL used `s.series.id`, but `LibrarySeason`'s actual field referencing its parent is named
`librarySeries` (`LibrarySeason.java:35`), not `series`; the contract and the new test use
`s.librarySeries.id` to match the real entity.

## What changed

- `story-009-api-contracts.md` — design decision 8 rewritten for the two-query split:
  - `LibrarySeriesRepository.findByUserId` now carries `@EntityGraph(attributePaths =
    {"seasons"})` only (single bag, safe on its own) instead of the round 1 two-level graph.
  - New repository `LibrarySeasonRepository`, with
    `@Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH s.episodes WHERE
    s.librarySeries.id IN :seriesIds") List<LibrarySeason>
    findSeasonsWithEpisodesByLibrarySeriesIdIn(@Param("seriesIds") List<Long> seriesIds)`. This is
    the first `@Query` method in this codebase; the contract now says so explicitly and specifies
    an explicit `@Param` rather than relying on `-parameters` compiler metadata, since there is no
    existing precedent in this project either way.
  - `LibraryService`'s constructor contract gains a third dependency,
    `LibrarySeasonRepository librarySeasonRepository`.
  - `LibraryService.list(...)`'s algorithm step 5 rewritten: call `findByUserId`, and only if it
    returns at least one series, collect the series ids and call
    `findSeasonsWithEpisodesByLibrarySeriesIdIn` once (not once per series) so the fetched
    `episodes` collections attach onto the already-managed `LibrarySeason` entities by entity
    identity - which only happens because both repository calls run inside this one
    `@Transactional(readOnly = true)` method, joining the same session under Spring's default
    `REQUIRED` propagation. `@Transactional(readOnly = true)` is unchanged from round 1 but is now
    explicitly documented as the mechanism the two-query split depends on, not just the explicit
    transactional boundary Dev originally asked for.
  - Still 2 queries total for the series branch per `GET /api/library` call regardless of library
    size - same asymptotic property as the round 1 proposal, without the bag conflict.

- `story-009-test-plan.md` — the single cross-story invariant row covering eager loading is split
  into three rows, one per link in the chain the round 1 single-query design used to cover in one
  claim:
  1. `findByUserId` alone initializes `seasons` (not `episodes`) with no open session.
  2. `findSeasonsWithEpisodesByLibrarySeriesIdIn` alone initializes `episodes` with no open
     session.
  3. The two calls, made from inside the same `LibraryService.list(...)` invocation, share one
     session so the episodes from call 2 attach onto the exact `LibrarySeason` instances call 1
     already loaded - the property a repository-level test calling each query independently
     cannot prove, since each would otherwise open its own transaction.

  The Test Strategy section's description of the eager-fetch tests is rewritten to match.

## Test file changes

- **`LibrarySeriesRepositoryEagerFetchTest`** (modified, not replaced) — the test method is
  renamed `should_haveSeasonsAlreadyInitialized_when_accessedAfterFindByUserIdReturns` and no
  longer asserts anything about `episodes`; under the round 2 design that is a separate call's
  responsibility. The fixture, the no-`@Transactional` isolation discipline, and the `@AfterEach`
  cleanup are all unchanged from round 1.

- **New: `LibrarySeasonRepositoryEagerFetchTest`** — same self-contained-transaction discipline as
  above, proving `findSeasonsWithEpisodesByLibrarySeriesIdIn`'s own `LEFT JOIN FETCH s.episodes`
  initializes `episodes` with no open session, called as an independent top-level call (not from
  inside a service method) to isolate this query's own fetch behavior.

- **New: `LibraryServiceEagerFetchIntegrationTest`** — the test closing the session-sharing gap
  neither repository-level test can close. Autowires the real `LibraryService` (not Mockito-mocked,
  unlike `LibraryServiceTest`, since a mocked repository cannot exercise real Hibernate
  session-sharing), seeds a series with a `WATCHED` season and a `PLANNED` season (a correct
  roll-up must be `CURRENTLY_WATCHING`; a silently-empty or partially-initialized `episodes`
  collection would roll up to the wrong status instead of throwing, which is why the assertion
  checks the specific resulting status rather than only "no exception"), and asserts
  `libraryService.list(userId, null, null, null, null)` returns that single item with
  `status == "CURRENTLY_WATCHING"`.

**What did not change:** no AC row changed - Concern 1 is, in Dev's own words, "a correctness
problem with the fix itself, not a new objection to the underlying design," about making
AC-1/AC-2/AC-4 pass at runtime rather than throw a different exception at the same call site.
`WatchStatusRollup` itself, the `LibraryService` merge/filter/sort/paginate algorithm, and every
DTO shape are untouched.

## Verification

Confirmed `LibrarySeries.seasons` and `LibrarySeason.episodes` are both `List`-typed in the real
entity source (`backend/src/main/java/com/streamvault/backend/library/LibrarySeries.java:53`,
`LibrarySeason.java:42`) before accepting Dev's claim. Ran `mvn test-compile` against the real
story-007/008 production code: the modified `LibrarySeriesRepositoryEagerFetchTest` and the two new
files (`LibrarySeasonRepositoryEagerFetchTest`, `LibraryServiceEagerFetchIntegrationTest`) each fail
only on "cannot find symbol" for the not-yet-implemented production code
(`LibrarySeasonRepository`, `LibraryService`, `LibraryListResponse`, `findByUserId`) - the same
expected Phase 1 RED state as the rest of this story, not a new or unrelated compile problem.

## RED state after this revision

`mvn test-compile` still fails, and still only on "cannot find symbol" for the not-yet-implemented
production classes/methods (`LibraryItemType`, `WatchStatusRollup`, `LibraryService`,
`LibraryController`, `LibraryListRequest`, `LibraryItemResponse`, `LibraryListResponse`,
`LibrarySeasonRepository`, and both existing repositories' `findByUserId`) - confirmed by running
`mvn test-compile` against the full module.

## Next step

Awaiting Dev's response to this revision. If Dev agrees, the next artifact is
`story-009-agreed.md` per the workflow, triggering implementation.
