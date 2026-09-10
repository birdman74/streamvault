# API Contracts — story-006: TMDB Search and Browse

## Overview

Adds a read-only catalog resource that proxies The Movie Database (TMDB). Two endpoints under
`/api/tmdb`: a free-text `search` and a query-less `browse` of curated lists. Both return the
**same response shape** so a UI can render either with one component (AC-3).

Neither endpoint is added to `SecurityConfig`'s `permitAll()` matcher list, so both fall under the
existing `anyRequest().authenticated()` rule (AC-8). **No `SecurityConfig` change is required or
permitted by this story.**

This story performs **no database access at all** — no repository, no entity, no migration. It
reads from TMDB and returns a projection. Nothing is written to any user's library (AC-7).

Result caching is allowed but not required (story notes). If Dev adds one it must be time-bounded
and must never serve one user a result set that misrepresents what TMDB currently returns. No
cache is assumed by these contracts.

## Media type values

The wire value is a plain JSON string, the name of the `TmdbMediaType` enum constant, matched
exactly (case-sensitive), consistent with the story-005 `RatingType` precedent:

| Wire value | Enum constant | TMDB `media_type` it maps from |
|---|---|---|
| `MOVIE` | `TmdbMediaType.MOVIE` | `movie` |
| `SERIES` | `TmdbMediaType.SERIES` | `tv` |

TMDB results whose `media_type` is `person` (or anything other than `movie` / `tv`) are dropped
before the response is built — this story surfaces titles only (AC-1, AC-2).

## Browse list values

| Wire value | Enum constant | TMDB endpoint it maps to |
|---|---|---|
| `POPULAR` | `TmdbBrowseList.POPULAR` | `GET {base-url}/trending/all/week` |
| `TRENDING` | `TmdbBrowseList.TRENDING` | `GET {base-url}/trending/all/day` |

Both TMDB trending endpoints return the same mixed movie/tv payload shape as `/search/multi`,
which is what lets `browse` reuse the exact mapping and response contract of `search` (AC-3). At
least one list (`POPULAR`, the default) fully satisfies AC-3; `TRENDING` is included to show the
resource genuinely browses *lists*, plural. Matching of the `list` value is case-sensitive and
exact (`popular` is rejected the same as a nonsense value), pinned so a later round does not
silently add case-insensitive binding as an undocumented behavior change.

## Result projection

Every result object, in both endpoints, has exactly these fields:

| Field | Type | Source / rule |
|---|---|---|
| `tmdbId` | number (long) | TMDB `id` |
| `mediaType` | string | `MOVIE` or `SERIES` per the table above |
| `title` | string | TMDB `title` for movies, `name` for series |
| `releaseYear` | number (int) or `null` | 4-digit year parsed from TMDB `release_date` (movies) or `first_air_date` (series). `null` when TMDB omits the date, sends `""`, or sends an unparseable value (AC-2: "where TMDB provides one") |
| `posterUrl` | string or `null` | `{image-base-url}` + TMDB `poster_path` when `poster_path` is present and non-null; `null` otherwise (AC-2) |

`releaseYear` and `posterUrl` are the only nullable fields. `tmdbId`, `mediaType`, and `title`
are always present.

## Endpoint: `GET /api/tmdb/search`

Free-text search across movies and TV series (AC-1).

### Request

| Param | In | Type | Rules |
|---|---|---|---|
| `query` | query string | string | **required**, not blank (`@NotBlank`). A missing param and a blank param are both rejected the same way. |
| `page` | query string | integer | optional, default `1`. `@Min(1)` and `@Max(500)` (TMDB's own hard page ceiling). Out-of-range or non-numeric is rejected before any TMDB call. |

Bound to a `TmdbSearchRequest` value object so validation failures route through the existing
`MethodArgumentNotValidException` handler and produce the same error envelope as the rest of the
API.

### Response — 200 OK

```json
{
  "page": 1,
  "totalPages": 12,
  "totalResults": 231,
  "results": [
    {
      "tmdbId": 27205,
      "mediaType": "MOVIE",
      "title": "Inception",
      "releaseYear": 2010,
      "posterUrl": "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg"
    },
    {
      "tmdbId": 1399,
      "mediaType": "SERIES",
      "title": "Game of Thrones",
      "releaseYear": 2011,
      "posterUrl": null
    }
  ]
}
```

- `page`, `totalPages`, `totalResults` are passed through from TMDB's response envelope
  (`page`, `total_pages`, `total_results`). They make every response explicitly bounded and
  navigable (AC-4). `results` holds a single TMDB page (max 20 items) — never an unbounded
  aggregation across pages.

### Response — 200 OK, no matches (AC-5)

A query that matches nothing is **not** an error:

```json
{
  "page": 1,
  "totalPages": 0,
  "totalResults": 0,
  "results": []
}
```

### Response — 400 Bad Request (blank or missing `query`)

Reuses the story-001 `MethodArgumentNotValidException` envelope:

```json
{
  "error": "Validation failed",
  "fields": {
    "query": "Search query is required"
  }
}
```

### Response — 400 Bad Request (`page` out of range)

```json
{
  "error": "Validation failed",
  "fields": {
    "page": "must be greater than or equal to 1"
  }
}
```

(The exact `page` message text is Bean Validation's default and is not pinned by tests; the
`error` key, the 400 status, and the presence of a `fields.page` entry are.)

### Response — 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point verbatim,
unchanged by this story (AC-8):

```json
{ "error": "Authentication required" }
```

### Response — 502 Bad Gateway (AC-6)

TMDB unreachable, timed out, or returned any non-2xx (including 401 for a bad key, 429 rate
limit, or 5xx). The upstream failure is never leaked as a raw 500; it is mapped to a single
user-facing message and the rest of the API keeps serving requests:

```json
{ "error": "The movie database is temporarily unavailable. Please try again in a moment." }
```

## Endpoint: `GET /api/tmdb/browse`

Curated-list browse with no free-text query (AC-3).

### Request

| Param | In | Type | Rules |
|---|---|---|---|
| `list` | query string | string | optional, default `POPULAR`. Must be `POPULAR` or `TRENDING`, matched exactly. Any other value is a 400. |
| `page` | query string | integer | optional, default `1`. Same `@Min(1)` / `@Max(500)` rule as `search`. |

Bound to a `TmdbBrowseRequest` value object.

### Response — 200 OK

Byte-for-byte the same shape as `search` (same envelope, same result projection). Example:

```json
{
  "page": 1,
  "totalPages": 500,
  "totalResults": 10000,
  "results": [
    {
      "tmdbId": 1184918,
      "mediaType": "MOVIE",
      "title": "The Wild Robot",
      "releaseYear": 2024,
      "posterUrl": "https://image.tmdb.org/t/p/w500/wTnV3PCVW5O92JMrFvvrRcV39RU.jpg"
    }
  ]
}
```

### Response — 400 / 401 / 502

Identical to `search`. A bad `list` value:

```json
{
  "error": "Validation failed",
  "fields": {
    "list": "must be POPULAR or TRENDING"
  }
}
```

(As with `page`, only the 400 status and the presence of a `fields.list` entry are pinned;
the message text is not.)

## New components (contract for Dev to implement against)

None of these exist yet. The story-006 tests are written against these names and **will not
compile until Dev implements them** — that compile/run failure is the expected RED state for
Phase 1, consistent with the story-002 and story-005 contracts.

### `com.streamvault.backend.tmdb.TmdbMediaType`
```java
enum TmdbMediaType { MOVIE, SERIES }
```

### `com.streamvault.backend.tmdb.TmdbBrowseList`
```java
enum TmdbBrowseList { POPULAR, TRENDING }
```

### `com.streamvault.backend.tmdb.dto.TmdbResult`
```java
record TmdbResult(long tmdbId, String mediaType, String title, Integer releaseYear, String posterUrl)
```
`mediaType` is `TmdbMediaType.name()`, not the enum, so the JSON value is always the plain
string regardless of Jackson configuration (same reasoning as story-005's `AccountSettingsResponse`).

### `com.streamvault.backend.tmdb.dto.TmdbResultPage`
```java
record TmdbResultPage(int page, int totalPages, int totalResults, List<TmdbResult> results)
```
`results` is never `null` — an empty match is `List.of()` (AC-5).

### `com.streamvault.backend.tmdb.dto.TmdbSearchRequest`
```java
record TmdbSearchRequest(
    @NotBlank(message = "Search query is required") String query,
    @Min(1) @Max(500) Integer page
) {}
```
`page` may be `null` (meaning "page 1"); `@Min` / `@Max` only fire on a non-null value.

### `com.streamvault.backend.tmdb.dto.TmdbBrowseRequest`
```java
record TmdbBrowseRequest(
    TmdbBrowseList list,
    @Min(1) @Max(500) Integer page
) {}
```
`list` may be `null` (meaning `POPULAR`). Spring's default `String` to enum binding is
case-sensitive by constant name; an unrecognized value becomes a binding error surfaced as a
400 with a `fields.list` entry.

### `com.streamvault.backend.tmdb.exception.TmdbUnavailableException`
```java
class TmdbUnavailableException extends RuntimeException {
    TmdbUnavailableException(Throwable cause);
    TmdbUnavailableException(String detail);
}
```
Thrown by the TMDB gateway on any `RestClientException` or non-2xx response. Caught by
`GlobalExceptionHandler`, mapped to **502 Bad Gateway** with the fixed message above. Per the
project "no swallowed exceptions" rule the original cause is attached (constructor arg) and
logged by the handler; it is never included in the response body.

### `com.streamvault.backend.tmdb.TmdbGateway`
```java
interface TmdbGateway {
    TmdbResultPage search(String query, int page);   // throws TmdbUnavailableException
    TmdbResultPage browse(TmdbBrowseList list, int page); // throws TmdbUnavailableException
}
```
The seam that owns the HTTP conversation with TMDB and the TMDB-JSON to `TmdbResultPage`
mapping, mirroring the `GoogleTokenVerifier` / `GoogleTokenInfoVerifier` split established in
story-002. Callers above this interface never see a TMDB wire type.

### `com.streamvault.backend.tmdb.RestClientTmdbGateway`
```java
@Component
class RestClientTmdbGateway implements TmdbGateway {
    RestClientTmdbGateway(
        RestClient.Builder restClientBuilder,
        @Value("${tmdb.base-url}") String baseUrl,
        @Value("${tmdb.api-key}") String apiKey,
        @Value("${tmdb.image-base-url:https://image.tmdb.org/t/p/w500}") String imageBaseUrl);
}
```
Constructor shape mirrors `GoogleTokenInfoVerifier` so it is testable with
`MockRestServiceServer.bindTo(builder)`. Authenticates to TMDB with the v3 `api_key` query
parameter (the credential in `.env.example` is named `TMDB_API_KEY`). Every request also carries
the caller's `query` / list / `page` params. Wraps `RestClientException` and non-2xx in
`TmdbUnavailableException`.

### `com.streamvault.backend.tmdb.TmdbCatalogService`
```java
@Service
class TmdbCatalogService {
    TmdbCatalogService(TmdbGateway tmdbGateway);
    TmdbResultPage search(String query, Integer page);        // page null -> 1
    TmdbResultPage browse(TmdbBrowseList list, Integer page);  // list null -> POPULAR, page null -> 1
}
```
Applies the defaults, then delegates to the gateway. Depends on **nothing else** — no
`UserRepository`, no `EntityManager`, no persistence bean of any kind (AC-7).

### `com.streamvault.backend.tmdb.TmdbController`
```java
@RestController
@RequestMapping("/api/tmdb")
class TmdbController {
    TmdbController(TmdbCatalogService tmdbCatalogService);

    @GetMapping("/search")
    ResponseEntity<TmdbResultPage> search(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Valid TmdbSearchRequest request);

    @GetMapping("/browse")
    ResponseEntity<TmdbResultPage> browse(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Valid TmdbBrowseRequest request);
}
```
`principal` is resolved but its `userId()` is **not** used to key any storage — it exists only so
the endpoint sits behind authentication and to keep the signature consistent with the rest of the
API. Nothing user-scoped is read or written (AC-7).

### `com.streamvault.backend.common.GlobalExceptionHandler` (extended)
One new method: `@ExceptionHandler(TmdbUnavailableException.class)` returning
`ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "<fixed message>"))` and
logging the cause. All existing `@ExceptionHandler` methods are untouched.

### Configuration (`application.yml`, extended)
```yaml
tmdb:
  api-key: ${TMDB_API_KEY}
  base-url: ${TMDB_BASE_URL:https://api.themoviedb.org/3}
  image-base-url: ${TMDB_IMAGE_BASE_URL:https://image.tmdb.org/t/p/w500}
```
`TMDB_API_KEY` and `TMDB_BASE_URL` already exist in `.env.example`. `TMDB_IMAGE_BASE_URL` is
new and optional (the yaml default covers local/dev); Dev should add it to `.env.example` for
completeness.

## Out of scope for this contract

- Any write path to a library (STORY-007, STORY-008 own that).
- Season / episode detail in results (STORY-008).
- Genre filtering, personalized recommendations, "because you watched" (story Out of Scope).
- Non-TMDB sources.
- The `SecurityConfig` filter chain itself — this story adds no `permitAll()` entry and changes
  no security bean.
- Frontend catalog UI and the "powered by TMDB" attribution placement — no frontend test
  project exists in this repo yet (same standing accepted gap as story-005).
- A `SecurityConfig`-level change to add `/api/tmdb/**` anywhere — explicitly not done; the
  routes rely on the existing `anyRequest().authenticated()` catch-all.
