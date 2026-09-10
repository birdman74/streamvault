# Agreed Design — STORY-006

## Summary
Test and Dev agree on the design after 1 round of review. Dev reviewed
`story-006-test-plan.md`, `story-006-api-contracts.md`, and the six already-committed failing
test classes (`TmdbControllerTest`, `TmdbCatalogServiceTest`, `RestClientTmdbGatewayTest`,
`dto/TmdbSearchRequestValidationTest`, `TmdbEndpointsSecurityTest`,
`TmdbPackageReadOnlyConventionTest`) against the PO story's AC-1..AC-8, the story notes, and the
existing codebase (`GlobalExceptionHandler`, `SecurityConfig`, `GoogleTokenInfoVerifier` +
`GoogleTokenInfoVerifierTest`, `AccountSettingsController` + `AccountSettingsControllerTest`,
`ControllerSliceTestAuthConventionTest`, ADR-001). No blocking concerns found. Proceeding to
implementation. One non-obvious framework point is documented below for Brian's visibility.

## Final API Contracts
As defined in `story-006-api-contracts.md`, unchanged. Key points confirmed against the tests:

- Two endpoints under `GET /api/tmdb`: `search` (required `query`, optional `page`) and `browse`
  (optional `list`, optional `page`). Both return the same `TmdbResultPage` envelope
  (`page`, `totalPages`, `totalResults`, `results[]`) and the same `TmdbResult` projection
  (`tmdbId`, `mediaType` as the `MOVIE`/`SERIES` string, `title`, nullable `releaseYear`,
  nullable `posterUrl`). `should_returnSameResultShapeForBrowseAsForSearch` pins byte-for-byte
  envelope equality, which follows automatically from both paths returning the same record type.
- Defaults are owned by `TmdbCatalogService`, not the controller: `page` null -> 1, `list` null
  -> `POPULAR`. `TmdbControllerTest` mocks `browse(null, null)` / `search("q", null)` while
  `TmdbCatalogServiceTest` asserts the null -> `POPULAR` / null -> 1 translation, and
  `TmdbEndpointsSecurityTest` exercises the full real chain resolving to
  `gateway.search("inception", 1)` / `gateway.browse(POPULAR, 1)`. The three layers are
  consistent.
- `TmdbGateway` is the HTTP seam (mirrors the `GoogleTokenVerifier` / `GoogleTokenInfoVerifier`
  split). `RestClientTmdbGateway` authenticates with the v3 `api_key` query parameter, maps
  `media_type` `movie` -> `MOVIE` and `tv` -> `SERIES`, drops every other `media_type`
  (`person`, etc.), takes `title` for movies and `name` for series, parses the 4-digit year from
  `release_date` / `first_air_date` (null on missing / `""` / unparseable), and prefixes
  `poster_path` with `${tmdb.image-base-url}` (null when absent). `POPULAR` -> `GET
  {base-url}/trending/all/week`, `TRENDING` -> `GET {base-url}/trending/all/day`; both TMDB
  trending endpoints return the same mixed payload shape as `/search/multi`, which is what lets
  `browse` reuse the `search` mapping verbatim (AC-3). Using `/trending/all/week` for `POPULAR`
  rather than `/movie/popular` is deliberate: `/movie/popular` returns movie-only objects with
  no `media_type` field and would fork the mapping.
- Any `RestClientException` (transport failure, connection reset) or non-2xx response (incl. 401
  for a bad key, 429, 5xx) is wrapped in `TmdbUnavailableException` inside the gateway. The
  original cause is attached to the exception (no swallowed exceptions) and logged by the
  handler; it is never placed in the response body.
- `GlobalExceptionHandler` gains exactly one method:
  `@ExceptionHandler(TmdbUnavailableException.class)` -> 502 Bad Gateway with the fixed body
  `{"error":"The movie database is temporarily unavailable. Please try again in a moment."}`.
  `TmdbUnavailableException extends RuntimeException`, so this handler is strictly more specific
  than the existing `@ExceptionHandler(Exception.class)` and cannot alter any existing mapping.
- No `SecurityConfig` change. `/api/tmdb/**` is not added to `permitAll()`, so it falls under
  the existing `anyRequest().authenticated()` rule and the existing 401 entry point
  (`{"error":"Authentication required"}`) applies unchanged (AC-8).
- No database access: no entity, repository, repository method, or Flyway migration. The
  migration set stays at exactly `V1..V4`. `TmdbPackageReadOnlyConventionTest` is the durable
  guard (source scan for persistence tokens + migration count) for AC-7.
- Config: `application.yml` gains `tmdb.api-key: ${TMDB_API_KEY}`,
  `tmdb.base-url: ${TMDB_BASE_URL:https://api.themoviedb.org/3}`,
  `tmdb.image-base-url: ${TMDB_IMAGE_BASE_URL:https://image.tmdb.org/t/p/w500}`.
  `TMDB_IMAGE_BASE_URL` is new; Dev adds it to `.env.example` next to the existing
  `TMDB_API_KEY` / `TMDB_BASE_URL` entries.

## Non-obvious framework point (for Brian's visibility, not a blocker)

`TmdbSearchRequest` / `TmdbBrowseRequest` are the first validated request objects in this
codebase that bind from the query string rather than from `@RequestBody`. The controller
signature is `search(@AuthenticationPrincipal AuthenticatedUser principal, @Valid
TmdbSearchRequest request)` with no `@ModelAttribute` / `@RequestParam` annotation, so Spring
resolves the record as an implicit model attribute and uses constructor binding (Spring
Framework 6.1+, present in Boot 3.5.16). On a `@NotBlank` / `@Min` / `@Max` violation or a
`list` type-mismatch, the model-attribute resolver throws `MethodArgumentNotValidException`
(which is what the existing `GlobalExceptionHandler.handleValidation` already maps to the
`{"error":"Validation failed","fields":{...}}` envelope). `TmdbControllerTest`'s four 400 cases
and `TmdbEndpointsSecurityTest`'s real-chain 200 cases together pin this behavior, so the
RED -> GREEN transition will confirm the exception type in practice. If implementation surfaces
any deviation (e.g. a `BindException` that needs a new handler branch, or a need for an explicit
`@ModelAttribute`), Dev will raise it to Brian as a possible ADR rather than adding an
undocumented handler. The contract's "case-sensitive `list` matching" note is not pinned by a
test (`should_return400_when_browseListIsNotARecognizedValue` uses `bogus`, invalid in any
case); Dev will implement so only the exact enum constant names `POPULAR` / `TRENDING` bind,
consistent with the contract narrative and the story-005 `RatingType` precedent.

## Implementation Plan
1. New package `com.streamvault.backend.tmdb`:
   - `TmdbMediaType { MOVIE, SERIES }`, `TmdbBrowseList { POPULAR, TRENDING }`.
   - `dto/TmdbResult(long tmdbId, String mediaType, String title, Integer releaseYear, String posterUrl)`.
   - `dto/TmdbResultPage(int page, int totalPages, int totalResults, List<TmdbResult> results)`;
     `results` is always non-null (`List.of()` on an empty match).
   - `dto/TmdbSearchRequest(@NotBlank(message = "Search query is required") String query,
     @Min(1) @Max(500) Integer page)`.
   - `dto/TmdbBrowseRequest(TmdbBrowseList list, @Min(1) @Max(500) Integer page)`.
   - `exception/TmdbUnavailableException` with `(Throwable cause)` and `(String detail)`
     constructors.
   - `TmdbGateway` interface: `TmdbResultPage search(String query, int page)`,
     `TmdbResultPage browse(TmdbBrowseList list, int page)`.
2. `RestClientTmdbGateway implements TmdbGateway` (`@Component`), constructor
   `(RestClient.Builder, @Value ${tmdb.base-url}, @Value ${tmdb.api-key},
   @Value ${tmdb.image-base-url:...})`. Builds the `RestClient` from the injected builder so
   `MockRestServiceServer.bindTo(builder)` intercepts (the `GoogleTokenInfoVerifier` pattern).
   Private wire records for the TMDB JSON (`page`/`total_pages`/`total_results`/`results[]` with
   `id`, `media_type`, `title`, `name`, `release_date`, `first_air_date`, `poster_path`). Single
   `try/catch (RestClientException)` around `.retrieve().body(...)` re-throwing
   `TmdbUnavailableException(cause)`; `.retrieve()` already throws on non-2xx by default so 4xx
   and 5xx both land in that catch.
3. `TmdbCatalogService` (`@Service`), constructor `(TmdbGateway)`. Applies the null defaults and
   delegates. No other collaborator.
4. `TmdbController` (`@RestController`, `@RequestMapping("/api/tmdb")`), constructor
   `(TmdbCatalogService)`. `GET /search` and `GET /browse` as in the contract, each with
   `@AuthenticationPrincipal AuthenticatedUser principal` (resolved for the auth boundary only,
   `userId()` never used) and a `@Valid` request record.
5. Extend `GlobalExceptionHandler` with the single `TmdbUnavailableException` -> 502 method,
   logging the cause via the existing `log` field, following the
   `handleInvalidRatingType` style.
6. `application.yml` + `.env.example`: add the three `tmdb.*` properties / the new
   `TMDB_IMAGE_BASE_URL` var.

## Test Coverage Confirmation
All AC-1..AC-8 and every cross-story invariant are covered by Test's already-committed failing
tests, as mapped in `story-006-test-plan.md`. Dev will add lower-level unit tests for concerns
below the integration boundary:

- `RestClientTmdbGateway` year parsing edge cases beyond the two in
  `RestClientTmdbGatewayTest`: a full non-date string (`"unknown"`), a `null` JSON value for the
  date field, and a well-formed date whose leading token is not a 4-digit year -> all -> `null`
  `releaseYear`, per the contract rule.
- `RestClientTmdbGateway` result ordering: results are emitted in TMDB payload order after the
  `person` drop (the controller and gateway tests assume index 0/1 stability).
- `RestClientTmdbGateway` when TMDB returns a 2xx with a `null` / absent `results` array ->
  mapped to `List.of()`, not an NPE (defensive; TMDB should not do this but the gateway must
  fail closed to an empty page, not a 500).
- `TmdbMediaType` / `TmdbBrowseList` `name()` round-trip used by the mapper (guards against a
  later rename silently changing the wire value).
- `TmdbUnavailableException` retains the cause passed to the `(Throwable)` constructor (so the
  handler can log it) and the `(String)` constructor path used by the controller tests works
  without a cause.
