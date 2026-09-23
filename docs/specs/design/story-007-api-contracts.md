# API Contracts — story-007: Add Movie from TMDB to Library

## Overview

Adds the first write path to a per-user library. One endpoint, `POST /api/library/movies`, takes a
TMDB movie id plus an optional watch status, fetches just enough catalog data from TMDB to render
the entry offline (AC-2), and stores one row scoped to the authenticated user.

This is the first table in the schema other than `users`, the first foreign key into `users`, and
the first per-user data. It introduces:

- a new package `com.streamvault.backend.library`,
- a new Flyway migration `V5__create_library_movies_table.sql`,
- a shared `WatchStatus` enum (Planned / Currently Watching / Watched) used by this story and reused
  by STORY-010 and STORY-012,
- one new read-only method on the existing `TmdbGateway` (`movie(long)`), and a
  `TmdbTitleNotFoundException` for the "TMDB does not recognise this id" case (AC-7),
- three additive `@ExceptionHandler` methods on `GlobalExceptionHandler`.

No `SecurityConfig` change: `/api/library/**` falls under the existing
`anyRequest().authenticated()` rule (AC-1, AC-6). The `com.streamvault.backend.tmdb` package stays
persistence-free; all persistence lives in `com.streamvault.backend.library`.

## Spec clarification surfaced to Brian (AC-6 "and see")

AC-6 reads "A user can only add to, **and see**, movies in their own library, never another
user's." STORY-009 ("View and Filter My Library", prereq STORY-007 + STORY-008) owns the
user-facing library list/detail read surface, and viewing is not listed in this story's Out of
Scope. This contract therefore implements **the add path only** and treats the "see" half of AC-6
as an **isolation guarantee**, not a new read endpoint:

- every write is bound to `principal.userId()`; the client cannot supply a user id,
- every repository access is user-scoped (`...ByUserIdAndTmdbId`),
- tests prove one user's rows are invisible to another user's queries and that a second user adding
  the same TMDB id is a separate, independent row (AC-5).

If Brian wants a minimal `GET /api/library/movies` in this story rather than deferring the entire
read surface to STORY-009, Test will add the endpoint contract and its tests in a revision. Raised
here rather than resolved silently.

## Watch status values

Wire value is the plain `WatchStatus` enum constant name, matched case-sensitively, consistent with
the story-005 `RatingType` and story-006 `TmdbMediaType` precedent.

| Wire value | Enum constant | Meaning |
|---|---|---|
| `PLANNED` | `WatchStatus.PLANNED` | user plans to watch (the default) |
| `CURRENTLY_WATCHING` | `WatchStatus.CURRENTLY_WATCHING` | user is watching now |
| `WATCHED` | `WatchStatus.WATCHED` | user has watched it |

`WatchStatus` lives in `com.streamvault.backend.library` because it is a library concept shared
across the epic (STORY-010 sets it on movies, STORY-012 derives it for series). This story only
sets it at add time; changing it afterwards is STORY-010.

## Endpoint: `POST /api/library/movies`

Add a TMDB movie to the authenticated user's library (AC-1).

### Request

`Content-Type: application/json`, bound to an `AddMovieRequest` record.

| Field | Type | Rules |
|---|---|---|
| `tmdbId` | number (long) | **required** (`@NotNull`), **must be positive** (`@Positive`). A missing, null, zero, or negative value is a 400 before any TMDB or DB call. |
| `status` | string | optional. One of `PLANNED`, `CURRENTLY_WATCHING`, `WATCHED`. Omitted or JSON `null` defaults to `PLANNED` (AC-3). Any other string is a 400 with a clear message (see below). Bean Validation does **not** constrain this field; the service parses it. |

Any unknown JSON property (for example a client-supplied `userId`) is ignored — the record has no
such component and the owning user is always taken from the JWT principal (AC-6).

```json
{ "tmdbId": 27205, "status": "PLANNED" }
```

### Response — 201 Created

Body is the created library entry, carrying enough TMDB catalog data to render without another
TMDB call (AC-2):

```json
{
  "id": 1,
  "tmdbId": 27205,
  "title": "Inception",
  "releaseYear": 2010,
  "posterUrl": "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
  "status": "PLANNED",
  "addedAt": "2026-09-10T12:00:00Z"
}
```

| Field | Type | Source / rule |
|---|---|---|
| `id` | number (long) | server-assigned library row id |
| `tmdbId` | number (long) | echoes the request |
| `title` | string | from TMDB `/movie/{id}` `title`; always present |
| `releaseYear` | number (int) or `null` | 4-digit year parsed from TMDB `release_date`; `null` when TMDB omits / empties / sends an unparseable date (same rule as story-006) |
| `posterUrl` | string or `null` | `{image-base-url}` + TMDB `poster_path`; `null` when TMDB sends no `poster_path` |
| `status` | string | the stored `WatchStatus` name (`PLANNED` when the request omitted it) |
| `addedAt` | string (ISO-8601 instant) | when the row was created |

`releaseYear` and `posterUrl` are the only nullable fields. AC-2 requires "at minimum TMDB id,
title, release year, and poster reference" be stored; `releaseYear` / `posterUrl` are stored as
`null` when TMDB itself does not provide them, matching how story-006 already treats those two
fields.

A `Location: /api/library/movies/{id}` header is permitted but not required; tests pin the 201
status and the body, not the header.

### Response — 400 Bad Request (missing / non-positive `tmdbId`)

Reuses the existing `MethodArgumentNotValidException` envelope:

```json
{ "error": "Validation failed", "fields": { "tmdbId": "must not be null" } }
```

(Only the 400 status, the `error` key, and the presence of a `fields.tmdbId` entry are pinned; the
Bean Validation default message text is not.)

### Response — 400 Bad Request (unsupported `status`)

`status` present but not one of the three allowed values. Mirrors story-005's
`InvalidRatingTypeException` handling: the service throws `InvalidWatchStatusException`, mapped to
400 with a fixed, enumerated message. Nothing is written and TMDB is not called.

```json
{ "error": "Invalid status 'SOON'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED." }
```

Matching is case-sensitive: `planned` is rejected exactly like `SOON`.

### Response — 404 Not Found (TMDB does not recognise the id) — AC-7

TMDB's `/movie/{id}` returns 404 for that id (unknown id, or an id that is not a movie). The
gateway raises `TmdbTitleNotFoundException`, mapped to 404. Nothing is written.

```json
{ "error": "We could not find that movie on TMDB." }
```

This is distinct from a TMDB outage (below): a 404 is a definite "no such movie", a 5xx / transport
error / bad-key 401 is "TMDB is unavailable".

### Response — 409 Conflict (already in this user's library) — AC-4

The same TMDB movie is already in the calling user's library. No duplicate row is created. The
service checks `existsByUserIdAndTmdbId` before calling TMDB, and also treats a unique-constraint
violation on insert (a race) as this same error.

```json
{ "error": "This movie is already in your library." }
```

The same TMDB id in a *different* user's library is not a conflict (AC-5).

### Response — 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point verbatim,
unchanged by this story (AC-1, AC-6):

```json
{ "error": "Authentication required" }
```

### Response — 502 Bad Gateway (TMDB unavailable)

TMDB unreachable, timed out, or returned a non-2xx that is not a 404 (5xx, 429, or 401 for a bad
key). Reuses story-006's existing `TmdbUnavailableException` -> 502 mapping unchanged; nothing is
written and the rest of the API keeps serving.

```json
{ "error": "The movie database is temporarily unavailable. Please try again in a moment." }
```

## New and changed components (contract for Dev to implement against)

None of the `library` package exists yet; `TmdbGateway.movie`, `TmdbMovie`, and
`TmdbTitleNotFoundException` do not exist yet. The story-007 tests are written against these names
and **will not compile until Dev implements them** — that is the expected Phase 1 RED state,
consistent with story-002 / story-005 / story-006.

### `com.streamvault.backend.library.WatchStatus`
```java
enum WatchStatus { PLANNED, CURRENTLY_WATCHING, WATCHED }
```
Plain enum. The service parses a wire string with `WatchStatus.valueOf`, wrapping
`IllegalArgumentException` in `InvalidWatchStatusException` (same shape as story-005's
`parseRatingType`).

### `com.streamvault.backend.library.LibraryMovie` (`@Entity`, table `library_movies`)
```java
class LibraryMovie {
    Long id;                 // @Id @GeneratedValue(IDENTITY)
    Long userId;             // user_id,      NOT NULL, FK -> users(id)
    long tmdbId;             // tmdb_id,      NOT NULL
    String title;            // title,        NOT NULL
    Integer releaseYear;     // release_year, NULLABLE
    String posterUrl;        // poster_url,   NULLABLE
    WatchStatus status;      // status,       NOT NULL, @Enumerated(STRING)
    Instant addedAt;         // added_at,     NOT NULL

    LibraryMovie(Long userId, long tmdbId, String title, Integer releaseYear,
                 String posterUrl, WatchStatus status);  // sets addedAt = Instant.now()
    protected LibraryMovie();  // JPA
    // getters only
}
```
Unique constraint `(user_id, tmdb_id)` at the table level (AC-4). No JPA `unique=true` needed if
the migration declares the named constraint; the entity must still match the columns so
`ddl-auto: validate` passes.

### `com.streamvault.backend.library.LibraryMovieRepository`
```java
interface LibraryMovieRepository extends JpaRepository<LibraryMovie, Long> {
    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);
    Optional<LibraryMovie> findByUserIdAndTmdbId(Long userId, long tmdbId);
}
```
Both finder methods must handle "no match" without throwing (`false` / `Optional.empty()`) —
cross-story repository invariant.

### `com.streamvault.backend.library.dto.AddMovieRequest`
```java
record AddMovieRequest(
    @NotNull @Positive Long tmdbId,
    String status
) {}
```
`status` is a raw nullable `String`; not constrained by Bean Validation.

### `com.streamvault.backend.library.dto.LibraryMovieResponse`
```java
record LibraryMovieResponse(
    Long id, long tmdbId, String title, Integer releaseYear,
    String posterUrl, String status, Instant addedAt
) {}
```
`status` is `WatchStatus.name()`, not the enum (same reasoning as story-005's
`AccountSettingsResponse`).

### `com.streamvault.backend.library.exception.InvalidWatchStatusException`
```java
class InvalidWatchStatusException extends RuntimeException {
    InvalidWatchStatusException(String invalidValue);
    // message: "Invalid status '<value>'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED."
}
```
Message format mirrors `InvalidRatingTypeException` (value list derived from `WatchStatus.values()`,
comma-joined, trailing period). -> 400.

### `com.streamvault.backend.library.exception.DuplicateLibraryMovieException`
```java
class DuplicateLibraryMovieException extends RuntimeException {
    DuplicateLibraryMovieException();  // message: "This movie is already in your library."
}
```
-> 409.

### `com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException`
```java
class TmdbTitleNotFoundException extends RuntimeException {
    TmdbTitleNotFoundException(long tmdbId);  // message: "We could not find that movie on TMDB."
}
```
Raised by the gateway only on a TMDB `404`. Lives in the `tmdb` package (no persistence tokens, so
the `TmdbPackageReadOnlyConventionTest` source scan still passes). -> 404.

### `com.streamvault.backend.tmdb.dto.TmdbMovie`
```java
record TmdbMovie(long tmdbId, String title, Integer releaseYear, String posterUrl) {}
```
The single-movie projection returned by the gateway. `releaseYear` / `posterUrl` follow the exact
null rules of story-006's `TmdbResult`.

### `com.streamvault.backend.tmdb.TmdbGateway` (extended)
```java
interface TmdbGateway {
    TmdbResultPage search(String query, int page);           // unchanged
    TmdbResultPage browse(TmdbBrowseList list, int page);    // unchanged
    TmdbMovie movie(long tmdbId);                            // NEW
    // movie(...) throws TmdbTitleNotFoundException on a TMDB 404,
    // TmdbUnavailableException on any other RestClientException / non-2xx
}
```

### `com.streamvault.backend.tmdb.RestClientTmdbGateway` (extended)
Implements `movie(long)` as `GET {base-url}/movie/{tmdbId}?api_key=...`, mapping `title`,
`release_date` (leading 4-digit year, reusing the existing `parseYear`), and `poster_path`
(absolute via `imageBaseUrl`, reusing the existing rule). Catches the `404` specifically and throws
`TmdbTitleNotFoundException`; every other `RestClientException` / non-2xx wraps to
`TmdbUnavailableException`, exactly as `search` / `browse` already do.

### `com.streamvault.backend.library.LibraryMovieService`
```java
@Service
class LibraryMovieService {
    LibraryMovieService(LibraryMovieRepository repository, TmdbGateway tmdbGateway);

    LibraryMovieResponse addMovie(Long userId, Long tmdbId, String statusValue);
}
```
Order of operations:
1. Resolve status: `statusValue == null` -> `PLANNED`; else `WatchStatus.valueOf` wrapped, throwing
   `InvalidWatchStatusException` (no repo or gateway call on this path).
2. `if (repository.existsByUserIdAndTmdbId(userId, tmdbId))` -> throw
   `DuplicateLibraryMovieException` (no gateway call).
3. `TmdbMovie movie = tmdbGateway.movie(tmdbId);` — propagates `TmdbTitleNotFoundException` (404) or
   `TmdbUnavailableException` (502); nothing written on either.
4. Build `LibraryMovie(userId, tmdbId, movie.title(), movie.releaseYear(), movie.posterUrl(),
   status)` and `repository.save(...)`; a `DataIntegrityViolationException` from the unique
   constraint is caught and rethrown as `DuplicateLibraryMovieException` (race backstop).
5. Map the saved row to `LibraryMovieResponse`.

Depends only on the repository and the gateway — no `UserRepository`, no cross-user access.

### `com.streamvault.backend.library.LibraryMovieController`
```java
@RestController
@RequestMapping("/api/library/movies")
class LibraryMovieController {
    LibraryMovieController(LibraryMovieService libraryMovieService);

    @PostMapping
    ResponseEntity<LibraryMovieResponse> add(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Valid @RequestBody AddMovieRequest request) {
        LibraryMovieResponse body =
            libraryMovieService.addMovie(principal.userId(), request.tmdbId(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
```
The user id is always `principal.userId()`; it is never read from the request body (AC-6).

### `com.streamvault.backend.common.GlobalExceptionHandler` (extended)
Three new `@ExceptionHandler` methods, all additive; no existing mapping changes:

| Exception | Status | Body |
|---|---|---|
| `InvalidWatchStatusException` | 400 | `{ "error": ex.getMessage() }` |
| `DuplicateLibraryMovieException` | 409 | `{ "error": ex.getMessage() }` |
| `TmdbTitleNotFoundException` | 404 | `{ "error": "We could not find that movie on TMDB." }` |

`TmdbUnavailableException` -> 502 is already registered (story-006) and is reused unchanged.

### Flyway `V5__create_library_movies_table.sql`
```sql
CREATE TABLE library_movies (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    tmdb_id     BIGINT       NOT NULL,
    title       VARCHAR(500) NOT NULL,
    release_year INTEGER,
    poster_url  VARCHAR(500),
    status      VARCHAR(30)  NOT NULL,
    added_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_library_movies_user_tmdb UNIQUE (user_id, tmdb_id)
);
```
- `user_id NOT NULL` + FK: no orphan library rows; a row must belong to a real user (security-sensitive).
- `UNIQUE (user_id, tmdb_id)`: the database-level backstop for AC-4; leaves AC-5 (same id, different user) legal.
- `status NOT NULL` with no DB default: the application always sets it (default `PLANNED` in the service), and the column makes a missing value impossible.
- H2-in-PostgreSQL-mode compatible (same dialect the existing `@SpringBootTest` constraint tests run against).

### Configuration
No new properties. `movie(long)` reuses the existing `tmdb.base-url` / `tmdb.api-key` /
`tmdb.image-base-url`.

## Out of scope for this contract

- Changing a movie's status after add — STORY-010 (this story sets it once, at add time).
- Removing a library movie — STORY-011.
- Any TV series path — STORY-008 (the gateway's `movie(long)` hits `/movie/{id}`, which 404s for a
  series id; that is the AC-7 path, not a series feature).
- The user-facing library list / detail read surface — STORY-009 (see the clarification note above).
- Rating, notes, watch date, streaming source columns — STORY-013/014/015/016 add their own columns
  or tables later.
- Frontend "add to library" UI and TMDB attribution placement — no frontend test project exists in
  this repo (same standing gap as story-005 / story-006).
- MongoDB — explicitly excluded for the whole epic per `epic-personal-library.md`.
