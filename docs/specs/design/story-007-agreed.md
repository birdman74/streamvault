# Agreed Design — STORY-007

## Summary

Test and Dev agree on the design after 1 round of review. Dev reviewed
`story-007-test-plan.md`, `story-007-api-contracts.md`, and all ten already-committed failing
test classes (`dto/AddMovieRequestValidationTest`, `WatchStatusTest`, `LibraryMovieServiceTest`,
`RestClientTmdbGatewayMovieTest`, `LibraryMovieControllerTest`,
`LibraryMovieControllerPrincipalConventionTest`, `LibraryMoviesTableConstraintsTest`,
`LibraryMovieRepositoryTest`, `LibraryEndpointsSecurityTest`, plus the amended
`TmdbPackageReadOnlyConventionTest`) against the PO story's AC-1..AC-7, the story notes and Out
of Scope, and the existing codebase (`RestClientTmdbGateway`, `GlobalExceptionHandler`,
`SecurityConfig`, `AccountSettingsService` + `InvalidRatingTypeException`, `User` +
`V1__create_users_table.sql`, `TmdbUnavailableException`, `ControllerSliceTestAuthConventionTest`,
`testsupport/WithMockAuthenticatedUser`, ADR-001).

No blocking concerns found. The design is a close mirror of established precedent: the
`RatingType` / `parseRatingType` / `InvalidRatingTypeException` shape from story-005 for the
`WatchStatus` parse-and-reject path, the `TmdbGateway` / `RestClientTmdbGateway` /
`MockRestServiceServer` seam from story-006 for `movie(long)`, the `users` table conventions
(`BIGSERIAL PRIMARY KEY`, `TIMESTAMP NOT NULL DEFAULT now()` mapped to an `Instant` field,
`@Enumerated(STRING)` into a `VARCHAR` column) for `library_movies`, and ADR-001 Layer 1 / Layer
2 for the controller slice and security integration tests. Proceeding to implementation. Three
non-obvious implementation points are recorded below for Brian's visibility, and one spec
interpretation (AC-6 "and see") that Test already surfaced to Brian is noted as pending his
decision but not blocking the add path.

## Final API Contracts

As defined in `story-007-api-contracts.md`, unchanged. Key points confirmed against the tests:

- One endpoint, `POST /api/library/movies`, body bound to
  `AddMovieRequest(@NotNull @Positive Long tmdbId, String status)`, returning 201 with
  `LibraryMovieResponse(Long id, long tmdbId, String title, Integer releaseYear, String
  posterUrl, String status, Instant addedAt)`. `status` in both records is a raw nullable
  `String` on the wire (the enum name); Bean Validation does not constrain it
  (`AddMovieRequestValidationTest.should_notConstrainStatusStringValue...`), the service parses
  it.
- `WatchStatus { PLANNED, CURRENTLY_WATCHING, WATCHED }` lives in
  `com.streamvault.backend.library` (epic-shared: STORY-010 sets it on movies, STORY-012 derives
  it for series). `WatchStatusTest` pins exactly three constants and their names.
- Order of operations in `LibraryMovieService.addMovie(Long userId, Long tmdbId, String
  statusValue)`, matching the contract and the `LibraryMovieServiceTest` expectations:
  1. Resolve status: `null` -> `PLANNED`; otherwise `WatchStatus.valueOf` wrapped, throwing
     `InvalidWatchStatusException` on `IllegalArgumentException` (case-sensitive, so `"planned"`
     is rejected). No repository or gateway call on this path
     (`verifyNoInteractions(tmdbGateway, libraryMovieRepository)`).
  2. `existsByUserIdAndTmdbId(userId, tmdbId)` -> throw `DuplicateLibraryMovieException` (no
     gateway call; `should_checkForDuplicateBeforeCallingTmdb...`).
  3. `tmdbGateway.movie(tmdbId)` -> propagate `TmdbTitleNotFoundException` (AC-7) or
     `TmdbUnavailableException` unchanged, with no `save` (`verify(repository, never()).save`).
  4. Build `LibraryMovie(userId, tmdbId, title, releaseYear, posterUrl, status)` and
     `repository.save(...)`; a `DataIntegrityViolationException` from the unique constraint is
     caught and rethrown as `DuplicateLibraryMovieException` (race backstop,
     `should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces`).
  5. Map the saved row to `LibraryMovieResponse` (`status` is `WatchStatus.name()`).
  The service depends only on `LibraryMovieRepository` and `TmdbGateway` (constructor injection);
  no `UserRepository`, no cross-user access.
- `LibraryMovie` `@Entity` -> table `library_movies`: `Long id`
  (`@GeneratedValue(IDENTITY)`), `Long userId` (`user_id`, NOT NULL, FK), `long tmdbId`, `String
  title`, `Integer releaseYear` (nullable), `String posterUrl` (nullable), `WatchStatus status`
  (`@Enumerated(STRING)`, NOT NULL), `Instant addedAt` (NOT NULL). Public constructor sets
  `addedAt = Instant.now()`; `protected` no-arg for JPA; getters only. The entity must match `V5`
  exactly so `spring.jpa.hibernate.ddl-auto=validate` still passes on full context load
  (`LibraryMovieRepositoryTest`, `LibraryMoviesTableConstraintsTest`, and the existing
  `StreamvaultBackendApplicationTests`).
- `LibraryMovieRepository extends JpaRepository<LibraryMovie, Long>` with
  `boolean existsByUserIdAndTmdbId(Long, long)` and
  `Optional<LibraryMovie> findByUserIdAndTmdbId(Long, long)`; both return "no match" without
  throwing (`false` / `Optional.empty()`).
- `V5__create_library_movies_table.sql` exactly as in the contract:
  `id BIGSERIAL PRIMARY KEY`, `user_id BIGINT NOT NULL REFERENCES users (id)`,
  `tmdb_id BIGINT NOT NULL`, `title VARCHAR(500) NOT NULL`, `release_year INTEGER`,
  `poster_url VARCHAR(500)`, `status VARCHAR(30) NOT NULL`,
  `added_at TIMESTAMP NOT NULL DEFAULT now()`,
  `CONSTRAINT uq_library_movies_user_tmdb UNIQUE (user_id, tmdb_id)`. This matches the `users`
  table conventions in `V1` (`BIGSERIAL PRIMARY KEY`, `TIMESTAMP NOT NULL DEFAULT now()`) and
  the `rating_type VARCHAR(50)` enum-column precedent in `V4`. `VARCHAR(30)` comfortably fits the
  longest constant `CURRENTLY_WATCHING` (18 chars).
- `TmdbGateway` gains `TmdbMovie movie(long tmdbId)`; `search` / `browse` are untouched.
  `TmdbMovie(long tmdbId, String title, Integer releaseYear, String posterUrl)` in
  `com.streamvault.backend.tmdb.dto`, with the exact null rules of story-006's `TmdbResult`
  (`releaseYear` null on missing / empty / unparseable date; `posterUrl` null on absent
  `poster_path`).
- `RestClientTmdbGateway.movie(long)` issues `GET {base-url}/movie/{tmdbId}?api_key=...`, reusing
  the existing `parseYear` and `imageBaseUrl` prefixing. It catches a TMDB 404 specifically and
  throws `TmdbTitleNotFoundException`; every other `RestClientException` / non-2xx (401 bad key,
  429, 5xx, transport) wraps to `TmdbUnavailableException`, exactly as `search` / `browse`
  already do. See "Non-obvious implementation point 1" below.
- `TmdbTitleNotFoundException(long tmdbId)` in `com.streamvault.backend.tmdb.exception`, message
  `"We could not find that movie on TMDB."`, references no persistence API so the
  `TmdbPackageReadOnlyConventionTest` source scan still passes. All `library_movies` persistence
  lives in `com.streamvault.backend.library`.
- `InvalidWatchStatusException(String invalidValue)` in
  `com.streamvault.backend.library.exception`, message
  `"Invalid status '<value>'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED."`
  (value list derived from `WatchStatus.values()`, comma-joined, trailing period), mirroring
  `InvalidRatingTypeException`. `DuplicateLibraryMovieException()` in the same package, message
  `"This movie is already in your library."`
- `GlobalExceptionHandler` gains exactly three additive `@ExceptionHandler` methods, all
  `RuntimeException` subtypes so each is strictly more specific than the existing
  `@ExceptionHandler(Exception.class)` and none alters an existing mapping:

  | Exception | Status | Body |
  |---|---|---|
  | `InvalidWatchStatusException` | 400 | `{ "error": ex.getMessage() }` |
  | `DuplicateLibraryMovieException` | 409 | `{ "error": ex.getMessage() }` |
  | `TmdbTitleNotFoundException` | 404 | `{ "error": "We could not find that movie on TMDB." }` |

  `TmdbUnavailableException` -> 502 is reused unchanged from story-006. The existing
  `MethodArgumentNotValidException` -> 400 `{"error":"Validation failed","fields":{...}}` handler
  covers the missing / non-positive `tmdbId` case with no change.
- `LibraryMovieController` (`@RestController`, `@RequestMapping("/api/library/movies")`,
  constructor injection of `LibraryMovieService`). `@PostMapping` method takes
  `@AuthenticationPrincipal AuthenticatedUser principal` and `@Valid @RequestBody
  AddMovieRequest request`, calls
  `service.addMovie(principal.userId(), request.tmdbId(), request.status())`, returns
  `ResponseEntity.status(CREATED).body(...)`. The owning user id is always `principal.userId()`
  and is never read from the body; `AddMovieRequest` has no `userId` component so a client that
  sends one is ignored (`LibraryMovieControllerPrincipalConventionTest`).
- No `SecurityConfig` change: `/api/library/**` falls under the existing
  `anyRequest().authenticated()` rule and the existing 401 entry point
  (`{"error":"Authentication required"}`) applies unchanged (AC-1, AC-6). No `permitAll()`
  widening; `/api/health` and `/api/auth/me` boundaries stay unmoved
  (`LibraryEndpointsSecurityTest`).
- Config: no new properties. `movie(long)` reuses `tmdb.base-url` / `tmdb.api-key` /
  `tmdb.image-base-url`.
- Cross-story amendment acknowledged: `TmdbPackageReadOnlyConventionTest`'s migration pin moves
  from `V1..V4` to `V1..V5` with `V5__create_library_movies_table.sql` named, message and Javadoc
  reworded. The persistence-token source scan of the `tmdb` package is unchanged. No story-006 AC
  coverage is dropped.

## Non-obvious implementation points (for Brian's visibility, not blockers)

1. **404 discrimination inside `RestClientTmdbGateway`.** The existing `fetch(...)` helper wraps
   every `RestClientException` (which includes all `HttpClientErrorException` / `HttpServerErrorException`
   subtypes, since `.retrieve()` throws on non-2xx by default) into `TmdbUnavailableException`.
   `movie(long)` needs a finer split: a TMDB `404` must become `TmdbTitleNotFoundException`
   while `401` / `403` / `429` / `5xx` / transport failures stay `TmdbUnavailableException`.
   Dev will implement this by catching `org.springframework.web.client.HttpClientErrorException.NotFound`
   (a subclass of `RestClientException`) specifically and rethrowing `TmdbTitleNotFoundException`,
   with the existing broad `catch (RestClientException)` after it for everything else. The new
   method will not route through the shared `fetch(...)` helper (that helper is typed to
   `TmdbResultPage` and has no 404 branch); it gets its own small private fetch with the
   two-branch catch. `RestClientTmdbGatewayMovieTest` pins both sides of the split (404 -> not
   found; 500 / `IOException` / 401 -> unavailable), so the RED -> GREEN transition confirms the
   discrimination in practice. `search` / `browse` behaviour is byte-for-byte unchanged.

2. **Unique-constraint race backstop.** `SimpleJpaRepository.save` is `@Transactional` and, with
   an `IDENTITY` generator, issues (and flushes) the `INSERT` within the `save` call, so a
   `DataIntegrityViolationException` from `uq_library_movies_user_tmdb` surfaces from
   `repository.save(...)` and can be caught there and rethrown as
   `DuplicateLibraryMovieException`. This matches `LibraryMovieServiceTest`'s stub
   (`when(save(...)).thenThrow(new DataIntegrityViolationException(...))`). The end-to-end tests
   do not exercise a real concurrent race (hard to make deterministic); the schema-level
   `LibraryMoviesTableConstraintsTest.should_rejectSecondRow_when_sameUserAddsSameTmdbIdTwice`
   proves the constraint exists, and the service unit test proves the translation. If
   implementation shows the exception only surfaces at a later transaction boundary (it should
   not, given `IDENTITY`), Dev will raise it rather than adding a broader catch.

3. **`added_at TIMESTAMP` mapped to `Instant`.** This is the exact pattern `User.createdAt`
   already uses against `users.created_at` (`TIMESTAMP NOT NULL DEFAULT now()`), so it introduces
   no new schema convention and needs no ADR. The application constructor always sets `addedAt`;
   the DB default is a belt-and-braces backstop matching `V1`.

## Spec interpretation pending Brian (AC-6 "and see") — not blocking

Test surfaced this in `story-007-api-contracts.md` ("Spec clarification surfaced to Brian") and
STATUS.md rather than resolving it silently, which Dev agrees is the right call. AC-6 reads "A
user can only add to, **and see**, movies in their own library." STORY-009 ("View and Filter My
Library", prereq STORY-007 + STORY-008) owns the user-facing library read surface, and viewing is
not in this story's Out of Scope. This design implements the **add path only** and treats the
"see" half as an isolation guarantee: every write is bound to `principal.userId()`, every
repository access is user-scoped (`...ByUserIdAndTmdbId`), and tests prove one user's rows are
invisible to another user's queries (`LibraryMovieRepositoryTest.should_notFindAnotherUsersMovie...`,
`LibraryEndpointsSecurityTest.should_persistAcrossUsersIndependently...`). Dev cannot override a
PO acceptance criterion; if Brian wants a minimal `GET /api/library/movies` in this story rather
than deferring the entire read surface to STORY-009, Test adds the endpoint contract and its
failing tests in a revision and Dev implements it in the same story. Flagged here so the decision
is explicit; it does not block starting the add path.

## Implementation Plan

1. New package `com.streamvault.backend.library`:
   - `WatchStatus { PLANNED, CURRENTLY_WATCHING, WATCHED }` (plain enum).
   - `LibraryMovie` `@Entity` (`@Table(name = "library_movies")`) with the fields, public
     all-args constructor setting `addedAt = Instant.now()`, `protected` no-arg constructor,
     getters only.
   - `LibraryMovieRepository extends JpaRepository<LibraryMovie, Long>` with the two finder
     methods.
   - `dto/AddMovieRequest(@NotNull @Positive Long tmdbId, String status)`.
   - `dto/LibraryMovieResponse(Long id, long tmdbId, String title, Integer releaseYear, String
     posterUrl, String status, Instant addedAt)`.
   - `exception/InvalidWatchStatusException` (message from `WatchStatus.values()`, mirrors
     `InvalidRatingTypeException`) and `exception/DuplicateLibraryMovieException` (fixed
     message).
   - `LibraryMovieService` (`@Service`, constructor `(LibraryMovieRepository, TmdbGateway)`),
     `addMovie` implementing the five-step order above, with a private
     `parseWatchStatus(String)` helper in the `parseRatingType` style.
   - `LibraryMovieController` (`@RestController`, `@RequestMapping("/api/library/movies")`,
     constructor `(LibraryMovieService)`), one `@PostMapping`.
2. Extend `com.streamvault.backend.tmdb`:
   - `dto/TmdbMovie(long tmdbId, String title, Integer releaseYear, String posterUrl)`.
   - `exception/TmdbTitleNotFoundException(long tmdbId)` (persistence-free).
   - `TmdbGateway`: add `TmdbMovie movie(long tmdbId)`.
   - `RestClientTmdbGateway`: implement `movie(long)` per point 1, reusing `parseYear` /
     `imageBaseUrl`, with a private wire record for the single-movie JSON
     (`id`, `title`, `release_date`, `poster_path`).
3. Extend `GlobalExceptionHandler` with the three additive `@ExceptionHandler` methods, in the
   `handleInvalidRatingType` style (400 / 409 from `ex.getMessage()`, 404 with the fixed
   message).
4. Add `backend/src/main/resources/db/migration/V5__create_library_movies_table.sql` exactly as
   contracted.
5. No `SecurityConfig`, `application.yml`, or `.env.example` change.

## Test Coverage Confirmation

All AC-1..AC-7 and every cross-story invariant are covered by Test's ten already-committed
failing classes, as mapped in `story-007-test-plan.md`. Dev will add lower-level unit tests for
concerns below Test's integration boundary:

- **`InvalidWatchStatusExceptionTest`** (mirrors `InvalidRatingTypeExceptionTest`): the message
  is exactly `"Invalid status 'SOON'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED."`,
  the value list is derived from `WatchStatus.values()` (so a later enum change flows through),
  and `getInvalidValue()` returns the offending string. `DuplicateLibraryMovieException` /
  `TmdbTitleNotFoundException` message constants pinned the same way, including that
  `TmdbTitleNotFoundException` retains the `tmdbId` it was constructed with.
- **Service ordering nuance not pinned by Test's suite**: `addMovie(user, tmdbId, "GARBAGE")`
  throws `InvalidWatchStatusException` (400) *before* the duplicate check, so an unsupported
  status on a movie that is already in the library still yields 400, never 409 — asserting
  `verifyNoInteractions(repository)` on that path.
- **Service response mapping**: `LibraryMovieResponse.status` is the `WatchStatus.name()` string
  (not the enum), `id` / `addedAt` are carried through from the saved entity, and `releaseYear` /
  `posterUrl` nulls survive the round-trip into the response (Test pins the entity side; Dev
  pins the DTO side explicitly).
- **`LibraryMovie` entity unit test**: the public constructor sets `addedAt` to a non-null
  `Instant` and leaves `id` null before persist; the `protected` no-arg constructor exists for
  JPA; the class exposes getters only (no setters), guarding the immutability assumption other
  stories will rely on.
- **`RestClientTmdbGatewayMovieTest` gap**: add a `403 Forbidden` case (distinct from the
  existing 401 / 404 / 500) to nail down that *only* 404 maps to `TmdbTitleNotFoundException`
  and every other 4xx stays `TmdbUnavailableException`; and a 2xx with an empty/`{}` body ->
  `title` null pass-through without an NPE (defensive; the service does not depend on it but the
  gateway must not 500).
