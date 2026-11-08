# Agreed Design — story-009

## Summary
Test and Dev agree on the design after 3 rounds of review. Implementation can begin.

## Final API Contracts
`story-009-api-contracts.md` as revised through round 2, with no further changes from round 3.
`GET /api/library` merges `LibraryMovieRepository.findByUserId` and
`LibrarySeriesRepository.findByUserId` in a new read-only `LibraryService`, filters by type/status
(AND semantics), sorts by `addedAt` desc / `type` asc / `id` asc, and paginates with `page`/`pageSize`
defaulting to `1`/`20`, capped at `100`. A series' status is always
`WatchStatusRollup.seriesStatus(...)`, never a stored column. The round 2 fetch strategy stands:
`LibrarySeriesRepository.findByUserId` carries `@EntityGraph(attributePaths = {"seasons"})` (single
bag, avoids `MultipleBagFetchException`); a new `LibrarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn`
eager-fetches episodes for a batch of series in one `SELECT DISTINCT ... LEFT JOIN FETCH` query;
`LibraryService.list(...)` is `@Transactional(readOnly = true)` so both calls share one Hibernate
session and the episodes attach onto the already-loaded `LibrarySeason` instances by entity identity.

## Round 3 Review

Dev independently re-verified the round 2 fix rather than re-reading the prior rounds' conclusions
at face value, specifically checking the one gap neither round's test description explicitly closed:
whether `findByUserId`'s `@EntityGraph(attributePaths = {"seasons"})` could return the same
`LibrarySeries` row duplicated once per season (the classic to-many-fetch Cartesian product problem
that is exactly why the round 2 contract adds `DISTINCT` to `LibrarySeasonRepository`'s own `@Query`).
Checked against external documentation of Spring Data JPA's actual `@EntityGraph` behavior on derived
finder methods (as opposed to an explicit `@Query` with `JOIN FETCH`, which is the case that genuinely
needs an explicit `DISTINCT`): Spring Data JPA's Criteria-API-based execution path for
`@EntityGraph`-annotated derived methods applies root-entity de-duplication automatically, unlike a
hand-written JPQL `JOIN FETCH` without `DISTINCT`. So `findByUserId`'s entity graph does not need (and
the contract never claimed it needed) an explicit `DISTINCT`; `LibrarySeasonRepository`'s own query
correctly needs and has one, since it is a hand-written `@Query`, not a derived method with an entity
graph. No defect found. Also re-verified `LibrarySeries`/`LibrarySeason`/`LibraryMovie`/`WatchStatus`
against the actual committed source (not just the contract's prose) and confirmed every field name,
enum constant, repository method signature, `AuthenticatedUser` shape, `SecurityConfig` `permitAll()`
boundary, and `GlobalExceptionHandler`'s existing `MethodArgumentNotValidException` mapping match what
the contract assumes. No gap found between the already-committed Phase 1 test files and the contract
they test against.

No new concerns raised. Rounds 1 and 2 already extracted the two substantive runtime-correctness
issues this design had (`LazyInitializationException` under `open-in-view: false`, then
`MultipleBagFetchException` from the first fix's two-level entity graph); both are resolved by the
round 2 two-query split.

## Implementation Plan
1. `LibraryItemType` enum (`MOVIE`, `SERIES`).
2. `WatchStatusRollup` utility (`of(List<WatchStatus>)`, `seriesStatus(LibrarySeries)`).
3. Extend `LibraryMovieRepository` with `findByUserId(Long userId)`.
4. Extend `LibrarySeriesRepository` with `@EntityGraph(attributePaths = {"seasons"}) findByUserId(Long userId)`.
5. New `LibrarySeasonRepository` with `findSeasonsWithEpisodesByLibrarySeriesIdIn(List<Long> seriesIds)`.
6. New DTOs: `LibraryListRequest`, `LibraryItemResponse`, `LibraryListResponse`.
7. New `LibraryService` implementing the merge/filter/sort/paginate algorithm per the contract,
   `@Transactional(readOnly = true)` on `list(...)`.
8. New `LibraryController` at `GET /api/library`, `principal.userId()` as the only source of the
   owning user id.
9. No `SecurityConfig`, `GlobalExceptionHandler`, or Flyway migration changes.

## Test Coverage Confirmation
All AC-1 through AC-8 are covered by Test's failing tests per `story-009-test-plan.md`'s coverage
table. Dev will add lower-level unit tests beyond Test's suite for:
- `WatchStatusRollup` edge cases not already pinned by `WatchStatusRollupTest` that surface during
  implementation (if any).
- Constructor/dependency-injection wiring of the new three-repository `LibraryService` (constructor
  injection only, per project convention).
