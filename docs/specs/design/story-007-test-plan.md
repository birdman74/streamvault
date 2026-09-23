# Test Plan — story-007: Add Movie from TMDB to Library

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | A signed-in user can add a TMDB movie search/browse result to their own library | `LibraryMovieControllerTest.should_return201WithTheCreatedEntry_when_movieIsAdded`; `LibraryMovieControllerTest.should_threadAuthenticatedUserIdIntoTheService_when_adding`; `LibraryEndpointsSecurityTest.should_return201_when_addMovieIsCalledWithAValidJwt`; `LibraryEndpointsSecurityTest.should_return401_when_addMovieIsCalledWithoutAuthentication`; `LibraryMovieServiceTest.should_persistTmdbCatalogDataAndReturnIt_when_movieIsAddedByAuthenticatedUser` |
| AC-2 | Stores enough TMDB data to render the entry with no further TMDB call: at minimum TMDB id, title, release year, poster reference | `LibraryMovieServiceTest.should_persistTmdbCatalogDataAndReturnIt_when_movieIsAddedByAuthenticatedUser`; `LibraryMovieServiceTest.should_storeNullReleaseYearAndPoster_when_tmdbOmitsThem`; `LibraryMovieControllerTest.should_return201WithTheCreatedEntry_when_movieIsAdded` (asserts `tmdbId`, `title`, `releaseYear`, `posterUrl`, `status`, `addedAt` in the body); `RestClientTmdbGatewayMovieTest.should_sendApiKeyToTheMovieEndpoint_when_movieIsCalled`; `RestClientTmdbGatewayMovieTest.should_mapTheSingleMovieProjection_when_tmdbReturnsTheMovie`; `RestClientTmdbGatewayMovieTest.should_returnNullPoster_when_tmdbOmitsPosterPath`; `RestClientTmdbGatewayMovieTest.should_returnNullYear_when_tmdbReleaseDateIsMissingOrEmpty`; `LibraryMovieRepositoryTest.should_roundTripALibraryMovie_when_savedThenLookedUpByUserAndTmdbId`; `LibraryMoviesTableConstraintsTest.should_acceptRow_when_releaseYearAndPosterUrlAreNull` |
| AC-3 | On add, status is Planned / Currently Watching / Watched; if the user does not choose, it defaults to Planned | `WatchStatusTest.should_defineExactlyThreeStatuses_when_valuesAreListed`; `WatchStatusTest.should_containThePlannedCurrentlyWatchingAndWatchedConstants_when_valuesAreListed`; `AddMovieRequestValidationTest.should_acceptRequest_when_tmdbIdIsPresentAndStatusOmitted`; `LibraryMovieServiceTest.should_defaultStatusToPlanned_when_noStatusIsProvided`; `LibraryMovieServiceTest.should_storeChosenStatus_when_statusIsProvided`; `LibraryMovieServiceTest.should_throwInvalidWatchStatusException_when_statusIsNotARecognizedValue`; `LibraryMovieServiceTest.should_throwInvalidWatchStatusException_when_statusDiffersOnlyInCase`; `LibraryMovieControllerTest.should_passChosenStatusThroughToTheService_when_statusIsInBody`; `LibraryMovieControllerTest.should_return400WithClearMessage_when_statusIsUnsupported`; `LibraryEndpointsSecurityTest.should_defaultToPlanned_when_statusIsOmittedEndToEnd`; `LibraryEndpointsSecurityTest.should_storeChosenStatusEndToEnd_when_statusProvided`; `LibraryMoviesTableConstraintsTest.should_rejectRow_when_statusIsNull` |
| AC-4 | A given TMDB movie appears at most once in one user's library; a duplicate add returns a clear error and creates no duplicate | `LibraryMovieServiceTest.should_throwDuplicateLibraryMovieException_when_userAlreadyHasThatMovie`; `LibraryMovieServiceTest.should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces`; `LibraryMovieServiceTest.should_checkForDuplicateBeforeCallingTmdb_when_addingAMovie`; `LibraryMovieControllerTest.should_return409WithClearMessage_when_movieAlreadyInLibrary`; `LibraryMoviesTableConstraintsTest.should_rejectSecondRow_when_sameUserAddsSameTmdbIdTwice`; `LibraryEndpointsSecurityTest.should_persistAcrossUsersIndependently_when_twoUsersAddTheSameMovie` (third add by the same user -> 409) |
| AC-5 | The same TMDB movie can independently exist in different users' libraries, each with its own status and data | `LibraryMovieServiceTest.should_addIndependentlyForEachUser_when_twoUsersAddTheSameTmdbMovie`; `LibraryMoviesTableConstraintsTest.should_acceptRows_when_differentUsersAddTheSameTmdbId`; `LibraryMovieRepositoryTest.should_notFindAnotherUsersMovie_when_queryingByUserId`; `LibraryEndpointsSecurityTest.should_persistAcrossUsersIndependently_when_twoUsersAddTheSameMovie` |
| AC-6 | A user can only add to, and see, movies in their own library, never another user's | `LibraryMovieControllerPrincipalConventionTest.should_threadResolvedPrincipalUserIdIntoService_when_addingAMovie`; `LibraryMovieControllerPrincipalConventionTest.should_useADifferentPrincipalUserId_when_aDifferentUserAdds`; `LibraryMovieControllerPrincipalConventionTest.should_ignoreAnyUserIdInTheRequestBody_when_adding`; `LibraryMovieServiceTest.should_bindTheNewRowToTheCallingUserId_when_movieIsAdded`; `LibraryMovieRepositoryTest.should_notFindAnotherUsersMovie_when_queryingByUserId`; `LibraryEndpointsSecurityTest.should_return401_when_addMovieIsCalledWithoutAuthentication` + `should_return401WithTheSameBodyAsTheRestOfTheApi_when_libraryIsCalledUnauthenticated`. See "Spec clarification" — the "and see" half is covered as an isolation guarantee; the user-facing read surface is STORY-009. |
| AC-7 | Adding a movie whose TMDB id TMDB does not recognise returns a clear error and adds nothing | `RestClientTmdbGatewayMovieTest.should_throwTmdbTitleNotFoundException_when_tmdbReturns404ForTheId`; `LibraryMovieServiceTest.should_propagateTmdbTitleNotFound_when_tmdbDoesNotRecognizeTheId` (verifies `save` is never called); `LibraryMovieControllerTest.should_return404WithClearMessage_when_tmdbDoesNotRecognizeTheId`; `LibraryEndpointsSecurityTest.should_return404_when_addingATmdbIdTmdbDoesNotRecognize` (asserts the library table is still empty afterwards) |

## Cross-Story Invariants

This story touches shared infrastructure: it adds the **first non-`users` table**, the **first
foreign key into `users`**, the **first per-user data**, a **shared `WatchStatus` enum**, a **new
`TmdbGateway` method**, and **three new `GlobalExceptionHandler` mappings**.

| Invariant | Test(s) |
|---|---|
| Schema change — a security-sensitive column (`library_movies.user_id`, which scopes every row to an owner) must have enforcement below the application. `user_id` is `NOT NULL` and a FK to `users(id)`. | `LibraryMoviesTableConstraintsTest.should_rejectRow_when_userIdIsNull`; `LibraryMoviesTableConstraintsTest.should_rejectRow_when_userIdReferencesNoUser` |
| Schema change — AC-4 must hold even if the application check is bypassed or races: `UNIQUE (user_id, tmdb_id)` at the table level | `LibraryMoviesTableConstraintsTest.should_rejectSecondRow_when_sameUserAddsSameTmdbIdTwice`; `LibraryMoviesTableConstraintsTest.should_acceptRows_when_differentUsersAddTheSameTmdbId`; `LibraryMovieServiceTest.should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces` |
| Schema change — `status` is never nullable at rest; the AC-3 default is applied in the app and the column forbids a missing value | `LibraryMoviesTableConstraintsTest.should_rejectRow_when_statusIsNull`; `LibraryMovieServiceTest.should_defaultStatusToPlanned_when_noStatusIsProvided` |
| Schema change — the new `@Entity` must match `V5` exactly so `spring.jpa.hibernate.ddl-auto=validate` still passes on a full context load | `LibraryMovieRepositoryTest` and `LibraryMoviesTableConstraintsTest` (both `@SpringBootTest`, full context + Flyway + `validate`); plus the pre-existing `StreamvaultBackendApplicationTests` context-load smoke test re-running green in the Phase 3 suite |
| Repository invariant — new finder methods return "no match" without throwing | `LibraryMovieRepositoryTest.should_returnEmptyOptional_when_noMovieMatchesUserAndTmdbId`; `LibraryMovieRepositoryTest.should_returnFalse_when_existsIsCheckedForAMovieNotInLibrary` |
| `SecurityConfig` `permitAll()` is not widened: `/api/library/**` must require auth, and every pre-existing boundary must be unmoved | `LibraryEndpointsSecurityTest.should_return401_when_addMovieIsCalledWithoutAuthentication`; `...should_return401WithTheSameBodyAsTheRestOfTheApi_when_libraryIsCalledUnauthenticated` (asserts `{"error":"Authentication required"}` byte-for-byte, the existing entry point); `...should_stillPermitHealthWithoutAuthentication_when_libraryRoutesAreAdded`; `...should_stillAuthenticateTheAuthMeEndpoint_when_libraryRoutesAreAdded`. Layer 2 per ADR-001. No `SecurityConfig` source change is made or permitted. |
| ADR-001 controller-slice auth convention holds for the new slice test | Enforced automatically by the existing `ControllerSliceTestAuthConventionTest` (source-scans every `@WebMvcTest`, now also `LibraryMovieControllerTest` and `LibraryMovieControllerPrincipalConventionTest`); both use `@WithMockAuthenticatedUser` + `@Import(SecurityConfig.class)` + `addFilters = false`, mirroring `AccountSettingsControllerTest`. |
| Principal is the only source of the owning user id — no controller path takes a user id from the request | `LibraryMovieControllerPrincipalConventionTest` (all three methods) |
| `GlobalExceptionHandler` gains three `@ExceptionHandler` methods; every existing mapping is untouched | The three new mappings are covered by `LibraryMovieControllerTest` (400 / 404 / 409) and `LibraryEndpointsSecurityTest`. Existing mappings stay covered by `AccountSettingsControllerTest` (validation 400, `InvalidRatingTypeException` 400), `AuthControllerGoogleTest` (401 / 409 / 500), and `TmdbControllerTest` (502), all re-running unchanged in the Phase 3 full suite. Same additive-change reasoning story-006 used for its 502 mapping. |
| `TmdbGateway` gains `movie(long)`; `search` / `browse` behaviour is unchanged | `RestClientTmdbGatewayMovieTest` covers only the new method; the story-006 `RestClientTmdbGatewayTest`, `TmdbCatalogServiceTest`, `TmdbControllerTest`, `TmdbEndpointsSecurityTest` re-run unchanged in the Phase 3 suite. |
| The `com.streamvault.backend.tmdb` package stays persistence-free (story-006 AC-7). `TmdbTitleNotFoundException` and `TmdbMovie` are added there but reference no persistence API; all `library_movies` persistence lives in `com.streamvault.backend.library`. | `TmdbPackageReadOnlyConventionTest.should_notReferenceAnyPersistenceApi_when_scanningTheTmdbMainSourcePackage` (unchanged, still green). Its sibling `should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` is **updated** by this story — see below. |
| Story-006's Flyway pin must acknowledge V5 without losing its guard | `TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` is amended: `containsExactly(V1..V5)` with `V5__create_library_movies_table.sql` named, and its assertion message reworded to "the TMDB feature adds no migration; V5 belongs to STORY-007's `library_movies` table". This keeps the guard that nothing persistence-shaped is smuggled into the `tmdb` package while letting STORY-007's legitimate schema change land. Change is documented here and in STATUS.md, not silent; no story-006 AC coverage is dropped. |
| JWT generation is untouched | This story changes no auth/JWT code. `JwtServiceTest` and `SecurityConfigAuthFlowTest` re-run unchanged in the Phase 3 suite. |

## API Contracts

See `story-007-api-contracts.md`.

## Test Strategy

Layers mirror the established codebase patterns (story-002 / story-005 / story-006):

- **Value-object validation (`library/dto/AddMovieRequestValidationTest`)** — plain Jakarta
  `Validator`, no Spring context, mirrors `RegisterRequestValidationTest`. Pins `@NotNull` /
  `@Positive` on `tmdbId` and that `status` is *not* Bean-Validation-constrained (an arbitrary
  string produces no violation; the service owns that rejection).

- **Enum (`library/WatchStatusTest`)** — mirrors `RatingTypeTest`. Pins exactly the three
  constants and their names.

- **Service unit tests (`library/LibraryMovieServiceTest`)** — Mockito, mocks
  `LibraryMovieRepository` and `TmdbGateway`, mirrors `AccountSettingsServiceTest`. The home for
  the add algorithm: status default and rejection, duplicate-before-TMDB ordering, per-user
  independence, binding the row to the caller's id, propagation of `TmdbTitleNotFoundException`
  and `TmdbUnavailableException` with **no write**, the unique-constraint race backstop, and
  null `releaseYear` / `posterUrl` pass-through.

- **Gateway unit tests (`tmdb/RestClientTmdbGatewayMovieTest`)** — `MockRestServiceServer.bindTo`,
  no Spring context, a direct copy of the story-006 `RestClientTmdbGatewayTest` pattern. Pins the
  `GET /movie/{id}` request (path + `api_key`), the field mapping (`title`, leading-4-digit year,
  absolute poster), and the failure split: a `404` -> `TmdbTitleNotFoundException`; any other
  non-2xx / transport error -> `TmdbUnavailableException`. Fixed JSON literals, fully
  deterministic.

- **Controller slice test (`library/LibraryMovieControllerTest`)** —
  `@WebMvcTest(LibraryMovieController.class)`, `@AutoConfigureMockMvc(addFilters = false)`,
  `@Import(SecurityConfig.class)`, `@WithMockAuthenticatedUser`, `@MockitoBean
  LibraryMovieService`, `@MockitoBean JwtService`. Mirrors `AccountSettingsControllerTest`. Covers
  the 201 body, the principal's id and the status string threading into the service, the 400 / 404
  / 409 / 502 mappings. Cannot assert 401 (filters off) — that is Layer 2.

- **Principal convention test (`library/LibraryMovieControllerPrincipalConventionTest`)** — mirrors
  `AccountSettingsControllerPrincipalConventionTest`. Pins that the owning user id is always
  `principal.userId()` and never taken from the request body (AC-6).

- **Persistence constraint test (`library/LibraryMoviesTableConstraintsTest`)** — `@SpringBootTest`
  + H2 in `MODE=PostgreSQL` + `JdbcTemplate`, mirrors `UserTableConstraintsTest`. Drives raw
  inserts to pin the schema-level invariants: `UNIQUE (user_id, tmdb_id)`, `user_id` NOT NULL +
  FK, `status` NOT NULL, and that `release_year` / `poster_url` accept `NULL`.

- **Repository test (`library/LibraryMovieRepositoryTest`)** — `@SpringBootTest` + H2, autowires
  `LibraryMovieRepository` and `UserRepository`. Covers the empty-result invariant, a save ->
  find round-trip with every field including `status`, and that one user's rows are invisible to a
  query for another user's id (AC-6).

- **Security / end-to-end integration test (`library/LibraryEndpointsSecurityTest`)** —
  `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` (real filter chain), H2, real
  register -> login -> token, `@MockitoBean TmdbGateway` so no live TMDB call happens. Mirrors
  `TmdbEndpointsSecurityTest`. The AC-6 auth home and the `SecurityConfig` invariant home; also
  the full-stack home for AC-3 (default + chosen status persisted), AC-4 (same user re-add ->
  409), AC-5 (two users, same id, both 201), and AC-7 (unknown id -> 404, table still empty).

- **Convention test update (`tmdb/TmdbPackageReadOnlyConventionTest`)** — the Flyway pin is moved
  from V1..V4 to V1..V5 with `V5__create_library_movies_table.sql` named and the message reworded.
  The persistence-token source scan of the `tmdb` package is unchanged and still passes.

### RED state expectation

Nothing in `com.streamvault.backend.library` exists yet, and `TmdbGateway.movie`, `TmdbMovie`, and
`TmdbTitleNotFoundException` do not exist. The test module fails at `test-compile`. Additionally,
before the amendment `TmdbPackageReadOnlyConventionTest` would fail on the V5 migration file; after
the amendment it stays green only once Dev has actually added `V5__create_library_movies_table.sql`.
That compile/run failure is the expected Phase 1 RED, consistent with story-002 / story-005 /
story-006.

## Out of Scope

- Editing a movie's status after the initial add — STORY-010.
- Removing a movie from the library — STORY-011.
- Any TV series behaviour — STORY-008. The gateway's `movie(long)` deliberately hits `/movie/{id}`,
  which 404s for a series id (an AC-7 path), so no series handling is added or tested here.
- The user-facing library list / detail view — STORY-009. AC-6's "and see" is verified only as an
  isolation property (writes bound to the principal, every query user-scoped). See the "Spec
  clarification surfaced to Brian" section of the API contracts; if Brian wants a minimal `GET` in
  this story, Test adds it in a revision.
- Rating, notes, watch date, streaming source — later stories add their own columns / tables.
- The exact Bean Validation default message for `tmdbId` — tests pin the 400 status, the `error`
  key, and the presence of a `fields.tmdbId` entry, not the framework wording. The
  `InvalidWatchStatusException`, `DuplicateLibraryMovieException`, and `TmdbTitleNotFoundException`
  messages **are** pinned because the app owns them.
- TMDB response caching for `/movie/{id}` — allowed but not required by the story and not
  implemented against; if Dev adds one, Test adds freshness regression tests in Phase 3.
- Frontend "add to library" UI and TMDB attribution — no frontend test project exists in this repo
  (standing gap since story-005).
- MongoDB — excluded for the entire epic per `epic-personal-library.md`.
