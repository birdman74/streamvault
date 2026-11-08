# API Contracts — story-009: View and Filter My Library

## Overview

The first read path over the library built by STORY-007 (`library_movies`) and STORY-008
(`library_series` / `library_seasons` / `library_episodes`): one endpoint,
`GET /api/library`, returns a single bounded, paginated, filterable list that merges both
tables for the authenticated user (AC-1, AC-6, AC-7). This story adds no table and no Flyway
migration - purely additive read-only code over the existing schema.

This story adds:

- one new enum in `com.streamvault.backend.library` - `LibraryItemType` (`MOVIE` / `SERIES`) - the
  wire-facing discriminator for a merged list item. Kept distinct from `TmdbMediaType` (the
  `com.streamvault.backend.tmdb` package's catalog-type enum) even though the two currently share
  the same two constants: the `tmdb` package models what TMDB's catalog contains, the `library`
  package models what a user's library contains, and the two are allowed to diverge independently
  (for example if the library epic ever adds a non-TMDB source). Tying a user-facing library filter
  to a catalog package's enum would be the wrong dependency direction.
- one new stateless utility - `WatchStatusRollup` - that derives a `WatchStatus` from a list of
  child statuses using the exact rule specified by STORY-012 AC-2 / AC-3. See "Spec clarification"
  below for why this is being implemented now rather than in STORY-012.
- one new finder method on each of `LibraryMovieRepository` and `LibrarySeriesRepository` -
  `findByUserId(Long userId)` - both additive, no change to either entity or table.
- one new read-only service, `LibraryService`, and one new controller, `LibraryController`, at
  `GET /api/library`.
- zero new `GlobalExceptionHandler` mappings - every failure mode this endpoint can produce
  (invalid `type` / `status` / `page` / `pageSize`) already routes through the existing
  `MethodArgumentNotValidException` -> 400 mapping; there is no new exception type.

No `SecurityConfig` change: `/api/library` is not in the `permitAll()` set, so it falls under the
existing `anyRequest().authenticated()` rule exactly like `/api/library/movies` and
`/api/library/series` (AC-7).

## Design decisions made explicit

These are Test's implementation calls, not spec ambiguities requiring Brian's decision, recorded
here for Dev and Brian's visibility, same discipline as story-007/008.

1. **Default sort is `addedAt` descending (most recently added first), tie-broken by `type`
   ascending (`MOVIE` before `SERIES`), then by `id` ascending.** The story's own notes explicitly
   leave the exact default sort to Test. A merged list drawn from two independent primary-key
   sequences needs an explicit, fully deterministic tie-break or paginated results would not be
   stable across requests; `addedAt` alone is not guaranteed unique (two items added in the same
   millisecond), so a tie-break is mandatory, not optional.
2. **Filtering and sorting are performed by `LibraryService` in memory over each user's own rows,
   not by a cross-table SQL query.** `LibraryMovieRepository.findByUserId` and
   `LibrarySeriesRepository.findByUserId` each return that single user's full set of rows (bounded
   by one person's library, never by the whole table); the service merges, filters, sorts, and
   slices to the requested page before returning. This keeps the merge logic in one testable place
   and avoids a hand-written SQL `UNION` across two differently-shaped tables, which is a reasonable
   trade for a personal-use library size. AC-6's "bounded response" requirement is satisfied by the
   response itself always being capped at `pageSize`; it does not require the underlying per-user
   query to be paginated.
3. **A series' `status` for display and for status-filtering is always the roll-up value computed
   by `WatchStatusRollup`, never a stored column.** `library_series` and `library_seasons` have no
   `status` column (story-008's own design decision, unchanged). AC-2 requires showing "the rolled-up
   status per STORY-012" for a series; AC-4 requires filtering by status; the only correct status to
   filter a series against is therefore the same rolled-up value being displayed, not any
   lower-level or stored value.
4. **Pagination is `page` (1-based) and `pageSize`, both query parameters, defaulting to `1` and
   `20` with `pageSize` capped at `100`.** Mirrors the existing `TmdbSearchRequest` / `TmdbBrowseRequest`
   convention of nullable, bounds-checked `Integer` query parameters with service-side defaulting
   (`TmdbCatalogService`'s `page == null ? DEFAULT_PAGE : page` pattern). A hard `100` ceiling on
   `pageSize` is this story's own AC-6 enforcement, independent of whatever `pageSize` the client
   requests.
5. **A `page` number beyond the last available page is not an error.** It returns `200` with an
   empty `items` array and the correct `totalItems` / `totalPages`, the same treatment as AC-8's
   "filter combination that matches nothing" - there is no meaningful difference between the two
   cases from the API's point of view.
6. **`year` is the unified response field name for a movie's `releaseYear` and a series'
   `firstAirYear`.** The merged list has one shape; carrying two mutually-exclusive nullable year
   fields on every item would force every consumer to know which one applies for which `type`. The
   per-type responses (`LibraryMovieResponse.releaseYear`, `LibrarySeriesResponse.firstAirYear`) are
   untouched - this mapping exists only in `LibraryItemResponse`.
7. **`id` is unique only in combination with `type`.** `library_movies.id` and `library_series.id`
   are independent `BIGSERIAL` sequences and can collide (a movie and a series can both legitimately
   have `id = 1` for the same user). Any future story acting on one merged-list item must key off
   `(type, id)`, not `id` alone. Documented here so STORY-010 / STORY-011 / STORY-012 do not assume
   global uniqueness of the bare `id` this endpoint returns.
8. **`LibrarySeriesRepository.findByUserId` carries an explicit `@EntityGraph(attributePaths =
   {"seasons"})`; a new `LibrarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn(List<Long>
   seriesIds)` eager-fetches every episode for a batch of series in one query; and
   `LibraryService.list(...)` is `@Transactional(readOnly = true)` so both queries run inside the same
   Hibernate session.** Originally accepted from Dev's round 1 feedback
   (`story-009-dev-feedback-r1.md`, Concerns 1 and 2) as a single two-level `@EntityGraph`; revised
   again in round 2 (`story-009-dev-feedback-r2.md`, Concern 1) after Dev checked the entity source
   directly: `LibrarySeries.seasons` and `LibrarySeason.episodes` are both `List`-typed `@OneToMany`
   collections with `@OrderBy` and no `@OrderColumn` - Hibernate bags, not `Set`s or indexed lists.
   Requesting two nested `*ToMany` bag paths (`seasons` and `seasons.episodes`) in a single entity
   graph is the textbook `org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch
   multiple bags` trigger, thrown at query execution time against any series with at least one
   season - the normal case for AC-1, AC-2, and AC-4, not an edge case, and the exact case Test's own
   `LibrarySeriesRepositoryEagerFetchTest` was written to exercise.

   This project's `application.yml` sets `spring.jpa.open-in-view: false`, so the underlying problem
   from round 1 - the Hibernate session closing the moment a repository call returns, leaving
   `WatchStatusRollup.seriesStatus(...)` to hit uninitialized proxies with no session - is unchanged
   and still requires an explicit fetch strategy. The round 2 fix keeps the two concerns (bag conflict,
   N+1) resolved without touching either entity class (both stay genuinely read-only, per this
   story's own cross-story invariant), by splitting the single entity graph into two queries that
   share one session:
   1. `@EntityGraph(attributePaths = {"seasons"})` on `findByUserId` - a single bag path, safe on its
      own, run once per `list(...)` call.
   2. A second query, also run once per `list(...)` call (not once per series) - `LibrarySeasonRepository`
      is a new repository, `@Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH s.episodes
      WHERE s.librarySeries.id IN :seriesIds")`, called only when step 1 returned at least one series.
      Hibernate attaches the fetched `episodes` collections onto the already-managed `LibrarySeason`
      entities from step 1 by entity identity, because both queries run inside `LibraryService.list(...)`'s
      one `@Transactional(readOnly = true)` boundary: Spring's default `REQUIRED` propagation means
      each repository call joins that already-open transaction/session rather than opening its own,
      which is also why this fix only works if both calls happen from inside one `@Transactional`
      service method, not from two independent top-level calls. `WatchStatusRollup.seriesStatus(...)`
      then sees both collections already initialized with no further round trips.

   Still 2 queries total for the series branch per `GET /api/library` call regardless of how many
   series the user has - same asymptotic win as the original proposal, without the bag conflict.
   `@Transactional(readOnly = true)` on the service method is unchanged from round 1 and is now also
   the mechanism the two-query split depends on for session sharing, not just the explicit
   transactional boundary Dev originally asked the contract to state. This is the first `@Query` in
   this codebase; `seriesIds` is bound with an explicit `@Param("seriesIds")` rather than relying on
   `-parameters` compiler metadata, since there is no existing precedent either way in this project.

## Spec clarification already surfaced to Brian

**AC-2's "rolled-up status per STORY-012" when STORY-012 has not been implemented yet.** STORY-009's
own prerequisites are STORY-007 and STORY-008 only - STORY-012 is not listed, and in the epic's
story order STORY-012 comes after STORY-009. AC-2 nonetheless requires the series status shown in
this story's list to be "the rolled-up status per STORY-012," and AC-4 requires filtering by that
same status. This is a genuine cross-story sequencing question in the PO's spec, not something Test
is resolving silently: **this contract implements only the roll-up *computation* described by
STORY-012's own AC-2 / AC-3 text** (`WatchStatusRollup`, a pure, stateless derivation: Watched when
every child is Watched, Planned when every child is Planned, Currently Watching otherwise, with an
empty grouping treated as Planned) **as a read-only display and filter value**. Nothing in this
story adds a settable status for a season or series, a bulk-update action, or any STORY-012
acceptance criterion; those remain entirely STORY-012's to implement and claim. When STORY-012 is
designed, Test's contract for it should reuse `WatchStatusRollup` unchanged rather than re-deriving
the same rule a second time in a different place - flagged here so that story's design phase does
not duplicate this logic. Surfaced to Brian for visibility rather than decided silently, consistent
with "do not resolve PO spec ambiguities silently."

This is also the story that resolves the "and see" half of STORY-007 AC-6 and STORY-008 AC-7 left
open on those stories' PRs (`story-007-api-contracts.md`, `story-008-api-contracts.md`): this
endpoint is the user-facing read surface those contracts said STORY-009 would own. No further action
needed on this story's part beyond existing - the isolation guarantee those stories already built
(every write bound to `principal.userId()`) is exactly what this endpoint's own user-scoping relies
on.

## Response to Dev's round 1 Question 1

Dev asked whether repeated query params binding onto a `List<Enum>` record constructor parameter
(`?type=MOVIE&type=SERIES` -> `LibraryListRequest.type`) has precedent in this codebase, since
`TmdbBrowseRequest` / `TmdbSearchRequest` - cited by this contract as precedent for the
unrecognized-value-is-400 behavior - only ever bind a single scalar value, not a repeated/list-valued
one.

Confirmed experimentally rather than by citation: a throwaway `@WebMvcTest` spike (a scratch
top-level `@RestController` plus a `record SpikeRequest(List<SpikeType> type)`, no production code
touched, deleted before this revision was committed) against this project's exact dependency set
(Spring Boot 3.5.16 / Spring Framework 6.2.19) showed:

- `?type=MOVIE&type=SERIES` binds onto `List<SpikeType> type` correctly - both repeated values land
  in the list, in request order.
- `?type=MOVIE&type=BOGUS` (one recognized element, one not) does **not** throw a raw
  `MethodArgumentTypeMismatchException` that would bypass the existing handler. It still routes
  through `MethodArgumentNotValidException`, auto-detected by `@WebMvcTest` as it always is for this
  codebase's `@RestControllerAdvice`, producing the same `{"error": "Validation failed", "fields":
  {"type": "..."}}` 400 envelope as every other validated endpoint.

Both of `LibraryControllerTest`'s existing `should_bindRepeatedTypeQueryParamsIntoAList_...` and
`should_return400_when_typeIsNotARecognizedConstant` tests (committed in the Phase 1 RED state) rest
on exactly this mechanism; no change was needed to either test or to the `LibraryListRequest`
contract. No fallback (e.g. binding `List<String>` and converting manually) is needed.

## Endpoint: `GET /api/library`

Returns every item in the authenticated user's own library, movies and series together, optionally
filtered by type and/or status, as one bounded, paginated list (AC-1, AC-6, AC-7).

### Request

Query-string-bound to a `LibraryListRequest` record (implicit model attribute, same binding style
as `TmdbSearchRequest` / `TmdbBrowseRequest`); a binding or validation failure routes through the
existing `MethodArgumentNotValidException` handler (400, shared `fields` envelope).

| Param | Type | Rules |
|---|---|---|
| `type` | zero or more of `MOVIE`, `SERIES` | repeatable query param (`?type=MOVIE&type=SERIES`); absent or empty means both types (AC-3). Case-sensitive exact constant match; an unrecognized value is a 400 with `fields.type`. Confirmed experimentally (see "Response to Dev's round 1 Question 1" below), not just by analogy to `TmdbBrowseRequest`. |
| `status` | zero or more of `PLANNED`, `CURRENTLY_WATCHING`, `WATCHED` | repeatable query param (`?status=PLANNED&status=WATCHED`); absent or empty means all three statuses (AC-4). Case-sensitive exact constant match; an unrecognized value is a 400 with `fields.status`. |
| `page` | number (int) | optional, `@Min(1)`; a value below `1` is a 400. Defaults to `1` when omitted. |
| `pageSize` | number (int) | optional, `@Min(1) @Max(100)`; a value outside `[1, 100]` is a 400. Defaults to `20` when omitted. |

`type` and `status` may both be supplied together; active filters combine with AND semantics - an
item must match the type filter (if any) and the status filter (if any) to be included (AC-5).

Examples:

```
GET /api/library
GET /api/library?type=MOVIE
GET /api/library?type=MOVIE&type=SERIES&status=WATCHED
GET /api/library?status=PLANNED&status=CURRENTLY_WATCHING&page=2&pageSize=10
```

### Response — 200 OK

```json
{
  "page": 1,
  "pageSize": 20,
  "totalItems": 2,
  "totalPages": 1,
  "items": [
    {
      "id": 3,
      "type": "SERIES",
      "tmdbId": 1399,
      "title": "Game of Thrones",
      "year": 2011,
      "posterUrl": "https://image.tmdb.org/t/p/w500/got.jpg",
      "status": "CURRENTLY_WATCHING",
      "addedAt": "2026-11-05T09:00:00Z"
    },
    {
      "id": 1,
      "type": "MOVIE",
      "tmdbId": 27205,
      "title": "Inception",
      "year": 2010,
      "posterUrl": "https://image.tmdb.org/t/p/w500/inception.jpg",
      "status": "WATCHED",
      "addedAt": "2026-11-04T12:00:00Z"
    }
  ]
}
```

| Field | Type | Source / rule |
|---|---|---|
| `page` | number (int) | the effective page returned (the requested value, or the `1` default) |
| `pageSize` | number (int) | the effective page size returned (the requested value, or the `20` default) |
| `totalItems` | number (int) | count of items matching the active filters, across both tables, before pagination |
| `totalPages` | number (int) | `0` when `totalItems` is `0`; otherwise `ceil(totalItems / pageSize)` |
| `items` | array of `LibraryItemResponse` | never `null`; `[]` when nothing matches or `page` is beyond the last page (AC-8) |
| `items[].id` | number (long) | the underlying `library_movies.id` or `library_series.id` row id. Unique only combined with `type` (see design decision 7) |
| `items[].type` | string | `"MOVIE"` or `"SERIES"` (AC-2) |
| `items[].tmdbId` | number (long) | echoes the stored TMDB id |
| `items[].title` | string | always present (AC-2) |
| `items[].year` | number (int) or `null` | movie's `releaseYear` or series' `firstAirYear` (design decision 6) |
| `items[].posterUrl` | string or `null` | movie's or series' stored poster reference (AC-2) |
| `items[].status` | string | the movie's stored status, or the series' `WatchStatusRollup`-derived status (AC-2, design decision 3) |
| `items[].addedAt` | string (ISO-8601 instant) | the sort key (design decision 1) |

Sorted by `addedAt` descending, `type` ascending, `id` ascending (design decision 1).

### Response — 400 Bad Request (invalid `type` / `status` / `page` / `pageSize`)

Reuses the existing `MethodArgumentNotValidException` envelope, identical shape to every other
validated endpoint:

```json
{ "error": "Validation failed", "fields": { "type": "..." } }
```

### Response — 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point verbatim,
unchanged by this story (AC-7):

```json
{ "error": "Authentication required" }
```

No 404, 409, or 502 response exists for this endpoint: there is no TMDB call and no write, so
neither a "not found," a "conflict," nor an "upstream unavailable" case can occur.

## New and changed components (contract for Dev to implement against)

None of this exists yet; the story-009 tests are written against these names and **will not
compile until Dev implements them** - the expected Phase 1 RED state, consistent with
story-002/005/006/007/008.

### `com.streamvault.backend.library.LibraryItemType`
```java
public enum LibraryItemType {
    MOVIE,
    SERIES
}
```

### `com.streamvault.backend.library.WatchStatusRollup`
```java
public final class WatchStatusRollup {
    private WatchStatusRollup();  // no instances

    // Watched when every status is WATCHED, Planned when every status is PLANNED,
    // CURRENTLY_WATCHING otherwise. An empty list is PLANNED (STORY-012 AC-2/AC-3's rule,
    // "a season or series with zero episodes must not break roll-up; treat an empty grouping
    // as Planned").
    public static WatchStatus of(List<WatchStatus> statuses);

    // Two-level convenience: rolls every season (itself rolled up from its episodes) up to one
    // series-level WatchStatus, by applying of(...) twice.
    public static WatchStatus seriesStatus(LibrarySeries series);
}
```

### `com.streamvault.backend.library.LibraryMovieRepository` (extended)
```java
public interface LibraryMovieRepository extends JpaRepository<LibraryMovie, Long> {
    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);
    Optional<LibraryMovie> findByUserIdAndTmdbId(Long userId, long tmdbId);
    List<LibraryMovie> findByUserId(Long userId);  // NEW
}
```

### `com.streamvault.backend.library.LibrarySeriesRepository` (extended)
```java
public interface LibrarySeriesRepository extends JpaRepository<LibrarySeries, Long> {
    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);
    Optional<LibrarySeries> findByUserIdAndTmdbId(Long userId, long tmdbId);

    @EntityGraph(attributePaths = {"seasons"})
    List<LibrarySeries> findByUserId(Long userId);  // NEW - see design decision 8 (revised r2)
}
```
Both new finder methods must handle "no rows for this user" without throwing - `List.of()`, not
`null` and not an exception - the cross-story repository invariant, same as every existing finder
on both repositories. `findByUserId`'s `@EntityGraph` is mandatory, not optional: without it,
`series.getSeasons()` throws `LazyInitializationException` once the repository call's session
closes under this project's `spring.jpa.open-in-view: false` (design decision 8). It fetches only
`seasons`, not `seasons.episodes` - a two-level bag fetch here would throw
`MultipleBagFetchException` (design decision 8, revised round 2). Episode initialization is a
separate concern, handled by `LibrarySeasonRepository` below.

### `com.streamvault.backend.library.LibrarySeasonRepository` (new)
```java
public interface LibrarySeasonRepository extends JpaRepository<LibrarySeason, Long> {

    @Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH s.episodes "
            + "WHERE s.librarySeries.id IN :seriesIds")
    List<LibrarySeason> findSeasonsWithEpisodesByLibrarySeriesIdIn(
            @Param("seriesIds") List<Long> seriesIds);  // NEW - see design decision 8 (added r2)
}
```
New repository, added in round 2 - story-008 never needed one since saving a `LibrarySeries` root
cascades its seasons and episodes in one `save(...)` call. `findSeasonsWithEpisodesByLibrarySeriesIdIn`
must handle an empty `seriesIds` list without throwing (`LibraryService` is expected to only call it
when `findByUserId` returned at least one series, per design decision 8, but the method itself still
follows the same "no match is `List.of()`, not an exception" invariant as every other finder on
either repository). Must be called from within the same `@Transactional` boundary as the
`findByUserId` call whose results it is meant to populate (`LibraryService.list(...)`'s own
`@Transactional(readOnly = true)`) - called standalone, outside any already-open transaction, it
still returns correctly fetched `LibrarySeason` entities, but they will not be the same object
instances as those hanging off a separately-loaded `LibrarySeries.getSeasons()`.

### `com.streamvault.backend.library.dto.LibraryListRequest`
```java
public record LibraryListRequest(
        List<LibraryItemType> type,
        List<WatchStatus> status,
        @Min(1) Integer page,
        @Min(1) @Max(100) Integer pageSize) {
}
```

### `com.streamvault.backend.library.dto.LibraryItemResponse`
```java
public record LibraryItemResponse(
        Long id,
        String type,
        long tmdbId,
        String title,
        Integer year,
        String posterUrl,
        String status,
        Instant addedAt) {
}
```
`type` and `status` are plain constant-name strings (`LibraryItemType.name()` / `WatchStatus.name()`),
not the enum, so the JSON value is always the plain string regardless of Jackson configuration - same
reasoning as every other response in this codebase (`LibraryMovieResponse.status`,
`AccountSettingsResponse`, `TmdbResult.mediaType`).

### `com.streamvault.backend.library.dto.LibraryListResponse`
```java
public record LibraryListResponse(
        int page,
        int pageSize,
        int totalItems,
        int totalPages,
        List<LibraryItemResponse> items) {
}
```

### `com.streamvault.backend.library.LibraryService`
```java
@Service
public class LibraryService {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public LibraryService(LibraryMovieRepository libraryMovieRepository,
            LibrarySeriesRepository librarySeriesRepository,
            LibrarySeasonRepository librarySeasonRepository);  // librarySeasonRepository added r2

    @Transactional(readOnly = true)  // see design decision 8
    public LibraryListResponse list(Long userId, List<LibraryItemType> types,
            List<WatchStatus> statuses, Integer page, Integer pageSize);
}
```
Algorithm:
1. Resolve defaults: `page` null -> `1`; `pageSize` null -> `20`.
2. Resolve effective type set: `types` null or empty -> both `MOVIE` and `SERIES` are included.
3. Resolve effective status set: `statuses` null or empty -> all three `WatchStatus` values are
   included.
4. If `MOVIE` is in the effective type set, `libraryMovieRepository.findByUserId(userId)`; map each
   to a `LibraryItemResponse` (`status` = the stored `WatchStatus.name()`). Otherwise skip the
   movie query entirely.
5. If `SERIES` is in the effective type set: call `librarySeriesRepository.findByUserId(userId)`.
   If that returns at least one series, collect their ids and call
   `librarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn(seriesIds)` once (not once per
   series) so the already-loaded `LibrarySeries.getSeasons()` collections have their `episodes`
   populated by entity identity within the same session (design decision 8, revised round 2) - skip
   this second call entirely when `findByUserId` returned no series, since there is nothing to
   eager-fetch. Map each series to a `LibraryItemResponse` (`status` =
   `WatchStatusRollup.seriesStatus(series).name()`); no lazy access occurs on a closed session,
   because both repository calls run inside this method's own `@Transactional(readOnly = true)`
   boundary. Otherwise (SERIES not in the effective type set) skip the whole series branch,
   including the episode fetch.
6. Concatenate both lists, then keep only items whose `status` is in the effective status set
   (AC-4, AC-5).
7. Sort by `addedAt` descending, then `type` ascending, then `id` ascending (design decision 1).
8. `totalItems` = the filtered list's size (before slicing). `totalPages` = `0` when `totalItems`
   is `0`, else `ceil(totalItems / pageSize)`.
9. Slice to `[(page - 1) * pageSize, page * pageSize)`, clamped to the list's actual bounds; a
   start index at or beyond the list's size yields `[]`, never an exception (AC-8, design decision
   5).
10. Return the `LibraryListResponse`.

Depends only on the two repositories - no `UserRepository`, no `TmdbGateway`, no write of any kind
(this story is read-only).

### `com.streamvault.backend.library.LibraryController`
```java
@RestController
@RequestMapping("/api/library")
public class LibraryController {
    public LibraryController(LibraryService libraryService);

    @GetMapping
    public ResponseEntity<LibraryListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid LibraryListRequest request) {
        return ResponseEntity.ok(libraryService.list(
                principal.userId(), request.type(), request.status(),
                request.page(), request.pageSize()));
    }
}
```
The user id is always `principal.userId()`; there is no `userId` component on `LibraryListRequest`
for a client to supply in the first place (AC-7).

### Configuration

No new properties, no Flyway migration, no `SecurityConfig` change.

## Out of scope for this contract

- Setting or changing any status, including the series/season bulk-action shortcuts - STORY-010
  (movie) and STORY-012 (series/season/episode). This contract's `WatchStatusRollup` is a read-only
  derivation only; see "Spec clarification" above.
- Removing an item from the library - STORY-011.
- Watch date, notes, rating, streaming source on the list response - later stories add their own
  fields; this story's `LibraryItemResponse` carries only what AC-2 requires plus the sort key.
- Free-text search within the library, and filtering/grouping by streaming source, rating, or watch
  date - explicitly out of scope per the story's own "Out of Scope" section.
- Collections or named sub-libraries - explicitly out of scope per the story's own "Out of Scope"
  section.
- A per-user total-row-count ceiling or a cross-table SQL `UNION` query - design decision 2 accepts
  in-memory merge/filter/sort/paginate as adequate for this project's personal-use scale; revisit if
  that ever stops holding.
- Frontend library view/filter UI - no frontend test project exists in this repo (standing gap).
- MongoDB - excluded for the entire epic per `epic-personal-library.md`.
