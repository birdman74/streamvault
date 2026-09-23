# API Contracts — story-008: Add TV Series from TMDB to Library

## Overview

Adds the second write path into the per-user library, and the first that persists a tree rather
than a single row: one endpoint, `POST /api/library/series`, takes a TMDB series id, pulls the
series' full season and episode structure from TMDB, and stores it under the authenticated user.
Deliberately larger than story-007: every season and every episode is persisted at add time
(AC-2), each episode starting at `WatchStatus.PLANNED` (AC-4).

This story adds:

- three new tables in `com.streamvault.backend.library` — `library_series` (aggregate root),
  `library_seasons`, `library_episodes` — via `V6__create_library_series_tables.sql`,
- one new read-only method on `TmdbGateway` (`series(long)`), backed by `GET /tv/{id}` for
  series-level data plus a batched `GET /tv/{id}?append_to_response=season/{n1},season/{n2},...`
  fetch (up to 20 season numbers per call) for episodes, and a `TmdbSeriesNotFoundException` for the
  "TMDB does not recognise this id" case (AC-8). Revised in round 1 of design iteration per Dev's
  feedback — see `story-008-test-revision-r1.md`; originally one sequential
  `GET /tv/{id}/season/{n}` call per season,
- two additive `@ExceptionHandler` methods on `GlobalExceptionHandler`.

No `SecurityConfig` change: `/api/library/**` already falls under the existing
`anyRequest().authenticated()` rule from story-007 (AC-1, AC-7). The `com.streamvault.backend.tmdb`
package stays persistence-free; all persistence lives in `com.streamvault.backend.library`, exactly
as story-007 established.

`WatchStatus` is reused unchanged from story-007 (`PLANNED` / `CURRENTLY_WATCHING` / `WATCHED`).
This story only ever writes `PLANNED` at add time; there is no status field on the request (unlike
`AddMovieRequest`, which lets the caller choose) because the story spec gives the user no choice at
add time for a series (AC-4).

## Design decisions made explicit (per AC-4's "roll-up computation is specified in STORY-012" note
and AC-9)

These are Test's implementation calls, not spec ambiguities requiring Brian's decision — recorded
here so Dev and Brian can see the reasoning, per the "no PO acceptance criterion is overridden,
nothing is resolved silently that actually needs a decision" rule.

1. **No `status` column on `library_series` or `library_seasons`.** The story notes read: "Setting
   and rolling up status is STORY-012; this story only needs the initial Planned state on every
   episode plus the stored structure." Only `library_episodes.status` is stored (AC-4); the
   series/season "roll-up to Planned" is a natural consequence of every episode starting Planned,
   not a value this story computes or persists. STORY-012 owns both the roll-up algorithm and
   wherever it decides to store or derive the aggregate value.
2. **Season `0` (specials) is stored as its own season grouping, not excluded (AC-9).** TMDB
   already models specials as an ordinary entry in `seasons[]` with `season_number: 0`; treating it
   like any other season number needs no special-case code and loses no data. The gateway and
   service iterate over whatever season numbers TMDB's series payload lists, in the order TMDB
   returns them, with no filtering.

## Spec clarification already surfaced to Brian (AC-7 "and see") — same open question as story-007

AC-7 reads "A user can only add to, **and see**, series in their own library, never another user's."
This is the identical question Test raised for story-007's AC-6 (`story-007-api-contracts.md`,
still pending Brian's decision on PR #28 at the time of writing). This contract applies the same
answer for consistency: the **add path only**, with the "see" half implemented as an isolation
guarantee (every write bound to `principal.userId()`, every repository access user-scoped, tests
prove cross-user invisibility). Not re-raised as a separate open question — it is the same pending
decision, and STORY-009 (prereq STORY-007 **and** STORY-008) owns the read surface for both. If
Brian's eventual answer on story-007 adds a `GET`, the same shape is added here in a revision.

## Endpoint: `POST /api/library/series`

Add a TMDB series, with its full season/episode structure, to the authenticated user's library
(AC-1).

### Request

`Content-Type: application/json`, bound to an `AddSeriesRequest` record.

| Field | Type | Rules |
|---|---|---|
| `tmdbId` | number (long) | **required** (`@NotNull`), **must be positive** (`@Positive`). A missing, null, zero, or negative value is a 400 before any TMDB or DB call. |

Any unknown JSON property (for example a client-supplied `userId`) is ignored — the owning user is
always taken from the JWT principal (AC-7).

```json
{ "tmdbId": 1399 }
```

### Response — 201 Created

Body is the created library entry with its full season/episode tree (AC-2, AC-3):

```json
{
  "id": 1,
  "tmdbId": 1399,
  "title": "Game of Thrones",
  "firstAirYear": 2011,
  "posterUrl": "https://image.tmdb.org/t/p/w500/got.jpg",
  "addedAt": "2026-09-23T12:00:00Z",
  "seasons": [
    {
      "seasonNumber": 0,
      "episodes": [
        { "episodeNumber": 1, "title": "Series Recap", "status": "PLANNED" }
      ]
    },
    {
      "seasonNumber": 1,
      "episodes": [
        { "episodeNumber": 1, "title": "Winter Is Coming", "status": "PLANNED" },
        { "episodeNumber": 2, "title": "The Kingsroad", "status": "PLANNED" }
      ]
    }
  ]
}
```

| Field | Type | Source / rule |
|---|---|---|
| `id` | number (long) | server-assigned library row id |
| `tmdbId` | number (long) | echoes the request |
| `title` | string | from TMDB `/tv/{id}` `name`; always present |
| `firstAirYear` | number (int) or `null` | 4-digit year parsed from TMDB `first_air_date`; `null` when TMDB omits / empties / sends an unparseable date (same rule as story-006 / story-007) |
| `posterUrl` | string or `null` | `{image-base-url}` + TMDB `poster_path`; `null` when TMDB sends no `poster_path` |
| `addedAt` | string (ISO-8601 instant) | when the row was created |
| `seasons` | array of `LibrarySeasonResponse` | every season TMDB's series payload lists, in TMDB's order, including season `0` if present (AC-9) |
| `seasons[].seasonNumber` | number (int) | TMDB `season_number`, unchanged (0 for specials) |
| `seasons[].episodes` | array of `LibraryEpisodeResponse` | every episode TMDB's per-season payload lists, in TMDB's order |
| `seasons[].episodes[].episodeNumber` | number (int) | TMDB `episode_number` |
| `seasons[].episodes[].title` | string or `null` | TMDB episode `name`; `null` when TMDB omits it (AC-2: "episode title where TMDB provides one") |
| `seasons[].episodes[].status` | string | always `"PLANNED"` on add (AC-4) |

`firstAirYear` and `posterUrl` are the only nullable series-level fields, matching story-007's
`releaseYear` / `posterUrl` precedent. A `Location: /api/library/series/{id}` header is permitted
but not required; tests pin the 201 status and the body, not the header.

### Response — 400 Bad Request (missing / non-positive `tmdbId`)

Reuses the existing `MethodArgumentNotValidException` envelope, identical shape to story-007:

```json
{ "error": "Validation failed", "fields": { "tmdbId": "must not be null" } }
```

### Response — 404 Not Found (TMDB does not recognise the id) — AC-8

TMDB's `GET /tv/{id}` returns 404 for that id (unknown id, or an id that is a movie rather than a
series). The gateway raises `TmdbSeriesNotFoundException`, mapped to 404. Nothing is written. A
failure on a *later* batched season-episode call (`GET /tv/{id}?append_to_response=season/...`) is
**not** this case — the series id was already confirmed to exist, so any failure fetching episode
detail is treated as a TMDB outage (502 below), not "series not found".

```json
{ "error": "We could not find that series on TMDB." }
```

This is a distinct exception and message from story-007's `TmdbTitleNotFoundException` ("We could
not find that movie on TMDB.") — that exception and its message are pinned by
`TmdbTitleNotFoundExceptionTest` for the movie wording specifically and are not touched by this
story.

### Response — 409 Conflict (already in this user's library) — AC-5

The same TMDB series is already in the calling user's library. No duplicate rows (series, seasons,
or episodes) are created. The service checks `existsByUserIdAndTmdbId` before calling TMDB, and
also treats a unique-constraint violation on insert (a race) as this same error.

```json
{ "error": "This series is already in your library." }
```

The same TMDB id in a *different* user's library is not a conflict (AC-6).

### Response — 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point verbatim,
unchanged by this story (AC-1, AC-7):

```json
{ "error": "Authentication required" }
```

### Response — 502 Bad Gateway (TMDB unavailable)

TMDB unreachable, timed out, or returned a non-2xx that is not a 404 on the series call, or *any*
non-2xx / transport failure on a batched season-episode call. Reuses story-006's existing
`TmdbUnavailableException` -> 502 mapping unchanged; nothing is written and the rest of the API keeps
serving.

```json
{ "error": "The movie database is temporarily unavailable. Please try again in a moment." }
```

## New and changed components (contract for Dev to implement against)

None of this exists yet; the story-008 tests are written against these names and **will not
compile until Dev implements them** — the expected Phase 1 RED state, consistent with
story-002/005/006/007.

### `com.streamvault.backend.library.LibrarySeries` (`@Entity`, table `library_series`) — aggregate root
```java
class LibrarySeries {
    Long id;                          // @Id @GeneratedValue(IDENTITY)
    Long userId;                      // user_id,        NOT NULL, FK -> users(id)
    long tmdbId;                      // tmdb_id,        NOT NULL
    String title;                     // title,          NOT NULL
    Integer firstAirYear;             // first_air_year, NULLABLE
    String posterUrl;                 // poster_url,     NULLABLE
    Instant addedAt;                  // added_at,       NOT NULL
    List<LibrarySeason> seasons;      // @OneToMany(mappedBy="librarySeries", cascade=ALL, orphanRemoval=true)

    LibrarySeries(Long userId, long tmdbId, String title, Integer firstAirYear, String posterUrl);
    // sets addedAt = Instant.now(), seasons = new ArrayList<>()
    protected LibrarySeries();  // JPA
    void addSeason(LibrarySeason season);  // wires both sides (season.librarySeries = this)
    // getters only otherwise
}
```

### `com.streamvault.backend.library.LibrarySeason` (`@Entity`, table `library_seasons`)
```java
class LibrarySeason {
    Long id;
    LibrarySeries librarySeries;      // @ManyToOne, library_series_id, NOT NULL
    int seasonNumber;                 // season_number, NOT NULL (0 = specials, AC-9)
    List<LibraryEpisode> episodes;    // @OneToMany(mappedBy="librarySeason", cascade=ALL, orphanRemoval=true)

    LibrarySeason(int seasonNumber);  // sets episodes = new ArrayList<>()
    protected LibrarySeason();        // JPA
    void addEpisode(LibraryEpisode episode);  // wires both sides
    // getters only otherwise
}
```

### `com.streamvault.backend.library.LibraryEpisode` (`@Entity`, table `library_episodes`)
```java
class LibraryEpisode {
    Long id;
    LibrarySeason librarySeason;      // @ManyToOne, library_season_id, NOT NULL
    int episodeNumber;                // episode_number, NOT NULL
    String title;                     // title,          NULLABLE
    WatchStatus status;                // status,         NOT NULL, @Enumerated(STRING)

    LibraryEpisode(int episodeNumber, String title, WatchStatus status);
    protected LibraryEpisode();       // JPA
    // getters only
}
```

### `com.streamvault.backend.library.LibrarySeriesRepository`
```java
interface LibrarySeriesRepository extends JpaRepository<LibrarySeries, Long> {
    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);
    Optional<LibrarySeries> findByUserIdAndTmdbId(Long userId, long tmdbId);
}
```
Both finder methods must handle "no match" without throwing — cross-story repository invariant,
same as story-007's `LibraryMovieRepository`. Saving the root cascades seasons and episodes in one
`save(...)` call; no separate season/episode repository is needed by this story.

### `com.streamvault.backend.library.dto.AddSeriesRequest`
```java
record AddSeriesRequest(@NotNull @Positive Long tmdbId) {}
```
No `status` field — a series has no choice to make at add time (AC-4).

### `com.streamvault.backend.library.dto.LibraryEpisodeResponse`
```java
record LibraryEpisodeResponse(int episodeNumber, String title, String status) {}
```

### `com.streamvault.backend.library.dto.LibrarySeasonResponse`
```java
record LibrarySeasonResponse(int seasonNumber, List<LibraryEpisodeResponse> episodes) {}
```

### `com.streamvault.backend.library.dto.LibrarySeriesResponse`
```java
record LibrarySeriesResponse(
    Long id, long tmdbId, String title, Integer firstAirYear, String posterUrl,
    Instant addedAt, List<LibrarySeasonResponse> seasons) {}
```

### `com.streamvault.backend.library.exception.DuplicateLibrarySeriesException`
```java
class DuplicateLibrarySeriesException extends RuntimeException {
    DuplicateLibrarySeriesException();  // message: "This series is already in your library."
}
```
-> 409.

### `com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException`
```java
class TmdbSeriesNotFoundException extends RuntimeException {
    TmdbSeriesNotFoundException(long tmdbId);  // message: "We could not find that series on TMDB."
    long getTmdbId();
}
```
Raised by the gateway only when `GET /tv/{id}` itself returns a `404`. Lives in the `tmdb` package
(no persistence tokens, so `TmdbPackageReadOnlyConventionTest`'s source scan still passes). -> 404.
Distinct class from story-007's `TmdbTitleNotFoundException` (different message, different media
type) — additive, not a modification of the existing class.

### `com.streamvault.backend.tmdb.dto.TmdbEpisode`
```java
record TmdbEpisode(int episodeNumber, String title) {}
```

### `com.streamvault.backend.tmdb.dto.TmdbSeason`
```java
record TmdbSeason(int seasonNumber, List<TmdbEpisode> episodes) {}
```

### `com.streamvault.backend.tmdb.dto.TmdbSeries`
```java
record TmdbSeries(long tmdbId, String title, Integer firstAirYear, String posterUrl, List<TmdbSeason> seasons) {}
```
The full-tree projection returned by the gateway. `firstAirYear` / `posterUrl` follow the exact
null rules of story-006/007's existing fields. `TmdbEpisode.title` is `null` when TMDB omits `name`.

### `com.streamvault.backend.tmdb.TmdbGateway` (extended)
```java
interface TmdbGateway {
    TmdbResultPage search(String query, int page);           // unchanged
    TmdbResultPage browse(TmdbBrowseList list, int page);    // unchanged
    TmdbMovie movie(long tmdbId);                             // unchanged (story-007)
    TmdbSeries series(long tmdbId);                            // NEW
    // series(...) throws TmdbSeriesNotFoundException on a TMDB 404 for GET /tv/{id},
    // TmdbUnavailableException on any other RestClientException / non-2xx from that call
    // OR from any batched GET /tv/{id}?append_to_response=season/... call made while assembling
    // the season/episode tree.
}
```

### `com.streamvault.backend.tmdb.RestClientTmdbGateway` (extended)

**Revised in design round 1** per Dev's feedback (`story-008-dev-feedback-r1.md`) — see
`story-008-test-revision-r1.md` for the full rationale. Originally specified as one sequential
`GET /tv/{id}/season/{n}` call per season (N+1 calls total); this made request latency and failure
surface scale linearly with season count, which is a real cost for TMDB series entries running
20-30+ seasons. Revised to use TMDB's `append_to_response` parameter to batch episode fetches.

Implements `series(long)` in up to `1 + ceil(seasonCount / 20)` stages, neither routed through the
existing `TmdbResultPage`-typed `fetch(...)` helper (same reasoning as `movie(long)` in story-007 —
this needs its own catch shape):

1. `GET {base-url}/tv/{tmdbId}?api_key=...` for series-level data (`id`, `name`, `first_air_date`,
   `poster_path`, and a `seasons` array of `{season_number}` summaries). A `404` here (specifically
   `HttpClientErrorException.NotFound`) throws `TmdbSeriesNotFoundException`; any other
   `RestClientException` throws `TmdbUnavailableException`. If the `seasons` array is empty, stop
   here — no batch call is made (`TmdbSeries.seasons()` is an empty list).
2. Partition the season numbers from the `seasons` array, in the order TMDB lists them (including
   `0`, AC-9), into consecutive batches of at most 20. For each batch, issue
   `GET {base-url}/tv/{tmdbId}?api_key=...&append_to_response=season/{n1},season/{n2},...` (comma
   -separated `season/{n}` values, in batch order). TMDB embeds each requested season's full object,
   including its `episodes` array, under a top-level `season/{n}` key on the response; the gateway
   reads only `episodes[].episode_number` / `episodes[].name` from each and maps them to
   `TmdbEpisode`s, in the season order established in step 1. **Any** `RestClientException` on a
   batch call (including a 404, which should not happen for seasons TMDB itself just listed, but is
   treated consistently) throws `TmdbUnavailableException` — the series id is already confirmed
   valid, so a batch-fetch failure is an availability problem, not a "not found" one.

For the common case of a show with 20 or fewer seasons (nearly every series), this is exactly 2 HTTP
calls total instead of N+1. `first_air_date` reuses the existing `parseYear` helper; `poster_path`
reuses the existing `imageBaseUrl` prefixing rule. No caching between calls is required or
implemented.

### `com.streamvault.backend.library.LibrarySeriesService`
```java
@Service
class LibrarySeriesService {
    LibrarySeriesService(LibrarySeriesRepository repository, TmdbGateway tmdbGateway);

    LibrarySeriesResponse addSeries(Long userId, Long tmdbId);
}
```
Order of operations:
1. `if (repository.existsByUserIdAndTmdbId(userId, tmdbId))` -> throw
   `DuplicateLibrarySeriesException` (no gateway call, AC-5).
2. `TmdbSeries series = tmdbGateway.series(tmdbId);` — propagates `TmdbSeriesNotFoundException`
   (AC-8) or `TmdbUnavailableException`; nothing written on either path.
3. Build the aggregate: `new LibrarySeries(userId, tmdbId, series.title(), series.firstAirYear(),
   series.posterUrl())`; for each `TmdbSeason`, `new LibrarySeason(season.seasonNumber())` added via
   `addSeason(...)`; for each `TmdbEpisode` in that season, `new LibraryEpisode(episode.episodeNumber(),
   episode.title(), WatchStatus.PLANNED)` added via `addEpisode(...)` (AC-2, AC-4, AC-9).
4. `repository.save(...)`, cascading the whole tree; a `DataIntegrityViolationException` from the
   `(user_id, tmdb_id)` unique constraint is caught and rethrown as `DuplicateLibrarySeriesException`
   (race backstop, same pattern as story-007).
5. Map the saved aggregate to `LibrarySeriesResponse`.

Depends only on the repository and the gateway — no `UserRepository`, no cross-user access.

### `com.streamvault.backend.library.LibrarySeriesController`
```java
@RestController
@RequestMapping("/api/library/series")
class LibrarySeriesController {
    LibrarySeriesController(LibrarySeriesService librarySeriesService);

    @PostMapping
    ResponseEntity<LibrarySeriesResponse> add(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Valid @RequestBody AddSeriesRequest request) {
        LibrarySeriesResponse body = librarySeriesService.addSeries(principal.userId(), request.tmdbId());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
```
The user id is always `principal.userId()`; it is never read from the request body (AC-7).

### `com.streamvault.backend.common.GlobalExceptionHandler` (extended)
Two new `@ExceptionHandler` methods, both additive; no existing mapping changes:

| Exception | Status | Body |
|---|---|---|
| `DuplicateLibrarySeriesException` | 409 | `{ "error": ex.getMessage() }` |
| `TmdbSeriesNotFoundException` | 404 | `{ "error": "We could not find that series on TMDB." }` |

`TmdbUnavailableException` -> 502 is already registered (story-006) and is reused unchanged.

### Flyway `V6__create_library_series_tables.sql`
```sql
CREATE TABLE library_series (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    tmdb_id        BIGINT       NOT NULL,
    title          VARCHAR(500) NOT NULL,
    first_air_year INTEGER,
    poster_url     VARCHAR(500),
    added_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_series_user_tmdb UNIQUE (user_id, tmdb_id)
);

CREATE TABLE library_seasons (
    id                 BIGSERIAL PRIMARY KEY,
    library_series_id  BIGINT  NOT NULL REFERENCES library_series (id) ON DELETE CASCADE,
    season_number      INTEGER NOT NULL,
    CONSTRAINT uq_library_seasons_series_number UNIQUE (library_series_id, season_number)
);

CREATE TABLE library_episodes (
    id                 BIGSERIAL PRIMARY KEY,
    library_season_id  BIGINT       NOT NULL REFERENCES library_seasons (id) ON DELETE CASCADE,
    episode_number     INTEGER      NOT NULL,
    title              VARCHAR(500),
    status             VARCHAR(30)  NOT NULL,
    CONSTRAINT uq_library_episodes_season_number UNIQUE (library_season_id, episode_number)
);
```
- `library_series.user_id NOT NULL` + FK: same security-sensitive ownership guarantee as
  `library_movies` (story-007).
- `UNIQUE (user_id, tmdb_id)` on `library_series`: the database-level backstop for AC-5; AC-6 (same
  id, different user) stays legal.
- `ON DELETE CASCADE` from `library_seasons` to `library_series`, and from `library_episodes` to
  `library_seasons`: no orphaned season/episode rows are structurally possible, independent of
  whether any code path ever deletes a series (removal is STORY-011, out of scope here, but the
  schema must not allow orphans regardless of which story eventually deletes a row).
- `UNIQUE (library_series_id, season_number)` and `UNIQUE (library_season_id, episode_number)`: a
  season/episode number can only appear once per parent, matching AC-2's "each identified and
  labeled" requirement at the schema level.
- `library_episodes.status NOT NULL` with no DB default: the application always sets `PLANNED` at
  insert time (AC-4); the column forbids a missing value the same way `library_movies.status` does.
- `library_seasons` / `library_series` have **no** `status` column — see "Design decisions made
  explicit" above.
- H2-in-PostgreSQL-mode compatible, matching the existing `@SpringBootTest` constraint tests.

### Configuration
No new properties. `series(long)` reuses the existing `tmdb.base-url` / `tmdb.api-key` /
`tmdb.image-base-url`.

## Out of scope for this contract

- Setting or changing episode/season/series status after add, and the roll-up computation itself —
  STORY-012 (this story only sets every episode to `PLANNED` at add time).
- Removing a series from the library — STORY-011. (The schema's `ON DELETE CASCADE` is put in place
  now regardless, as a structural invariant, not a removal feature.)
- Re-syncing a series' structure when TMDB adds seasons/episodes later — STORY-018.
- The user-facing library list/detail read surface — STORY-009. Same AC-7 "and see" treatment as
  story-007's AC-6 (see clarification above).
- Rating, notes, watch date, streaming source — later stories add their own columns/tables.
- The exact Bean Validation default message for `tmdbId` — same pinning discipline as story-007
  (400 status, `error` key, presence of `fields.tmdbId`, not the framework wording).
- TMDB response caching across the multiple `series(long)` calls — allowed but not required or
  implemented against.
- Frontend "add to library" UI — no frontend test project exists in this repo (standing gap).
- MongoDB — excluded for the entire epic per `epic-personal-library.md`.
