# Agreed Design — story-008

## Summary

Test and Dev agree on the design after 1 round of review.

Round 1 raised a single concern: the original gateway contract made one sequential
`GET /tv/{id}/season/{n}` call per season (N+1 calls total), which scales request latency and
failure surface linearly with season count — a real cost given TMDB series entries running 20-30+
seasons are common (procedurals, soaps, long-running animated series). Test's round 1 revision
(`story-008-test-revision-r1.md`) accepted this and replaced the per-season call sequence with a
batched `GET /tv/{id}?append_to_response=season/{n1},season/{n2},...` fetch (up to 20 season
numbers per call), collapsing the common case (<=20 seasons) to exactly 2 HTTP calls total and
capping the worst case at `ceil(N/20)+1`. No AC or invariant coverage was dropped by the revision,
no response DTO shape changed, and no service/controller/schema layer was touched — contained
entirely to `RestClientTmdbGateway` and `RestClientTmdbGatewaySeriesTest`, exactly as scoped.

Reviewed the round 1 revision fresh for round 2, not just the single concern in isolation: schema
(FK/cascade/unique constraints at the table level for AC-5/AC-6/no-orphans), the exception split
(`TmdbSeriesNotFoundException` on the series call only, `TmdbUnavailableException` on any batch-call
failure including a 404, since the series id is already confirmed by that point), the season-0 and
null-title handling for AC-9/AC-2, and the AC-7 "and see" isolation-guarantee treatment (consistent
with the still-pending story-007 AC-6 precedent, not a new open question). No further concerns.

## Final API Contracts

`docs/specs/design/story-008-api-contracts.md` as revised by `story-008-test-revision-r1.md` is
final. One endpoint, `POST /api/library/series`, taking `{ "tmdbId": long }` and returning a 201
with the full `LibrarySeriesResponse` season/episode tree. `TmdbGateway.series(long)` fetches
series-level data via `GET /tv/{id}`, then episode detail via batched
`GET /tv/{id}?append_to_response=season/{n1},...,season/{n20}` calls, one per group of up to 20
season numbers in TMDB's listed order. Three new tables (`library_series`, `library_seasons`,
`library_episodes`) via `V6__create_library_series_tables.sql`. Two additive
`GlobalExceptionHandler` mappings (`DuplicateLibrarySeriesException` -> 409,
`TmdbSeriesNotFoundException` -> 404); `TmdbUnavailableException` -> 502 reused unchanged from
story-006.

## Implementation Plan

1. `V6__create_library_series_tables.sql` — three tables exactly as specified in the contracts doc.
2. `com.streamvault.backend.tmdb.dto`: `TmdbEpisode`, `TmdbSeason`, `TmdbSeries` records.
3. `com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException`.
4. `TmdbGateway.series(long)` + `RestClientTmdbGateway` implementation: series-detail call, then
   season-number partitioning into batches of at most 20, then one `append_to_response` call per
   batch, mapping only `episodes[].episode_number` / `episodes[].name` per requested season key.
   Reuses the existing `parseYear` and `imageBaseUrl` helpers unchanged.
5. `com.streamvault.backend.library`: `LibrarySeries` (aggregate root, `addSeason`),
   `LibrarySeason` (`addEpisode`), `LibraryEpisode` entities, cascade `ALL` + `orphanRemoval` on both
   `@OneToMany` sides.
6. `LibrarySeriesRepository` — `existsByUserIdAndTmdbId`, `findByUserIdAndTmdbId`.
7. `dto`: `AddSeriesRequest`, `LibraryEpisodeResponse`, `LibrarySeasonResponse`,
   `LibrarySeriesResponse`.
8. `DuplicateLibrarySeriesException`.
9. `LibrarySeriesService.addSeries(userId, tmdbId)` — duplicate-check-before-TMDB-call ordering,
   build the aggregate via `addSeason`/`addEpisode`, force `WatchStatus.PLANNED` on every episode
   regardless of any TMDB input, single cascading `save`, catch
   `DataIntegrityViolationException` on the unique-constraint race and rethrow as
   `DuplicateLibrarySeriesException`.
10. `LibrarySeriesController` — `POST /api/library/series`, `principal.userId()` is the only source
    of the owning user id, never the request body.
11. Two new `GlobalExceptionHandler` mappings, additive only.
12. Amend `TmdbPackageReadOnlyConventionTest`'s Flyway pin per Test's plan (`V1..V6`,
    `V6__create_library_series_tables.sql` named) — this is Test's file; Dev's job is only to make
    the migration exist so it passes, not to edit the test.

## Test Coverage Confirmation

All AC-1..AC-9 are covered by Test's failing tests per `story-008-test-plan.md`'s coverage table.
Dev will add unit tests for:

- Entity-level `addSeason` / `addEpisode` wiring (both-sides-set invariant) if not already exercised
  incidentally by `LibrarySeriesRepositoryTest`'s round-trip.
- `RestClientTmdbGateway`'s season-number-to-batch partitioning logic in isolation (pure function,
  no `MockRestServiceServer`), as a lower-level unit complement to
  `RestClientTmdbGatewaySeriesTest`'s HTTP-level batching assertions, covering boundary counts (0,
  1, 20, 21, 40, 41 seasons) that aren't all separately enumerated at the gateway-test level.
