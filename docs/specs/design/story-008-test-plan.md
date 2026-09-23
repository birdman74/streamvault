# Test Plan — story-008: Add TV Series from TMDB to Library

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | From a TMDB series search or browse result, a signed-in user can add that series to their own library | `LibrarySeriesControllerTest.should_return201WithTheCreatedEntryIncludingSeasonsAndEpisodes_when_seriesIsAdded`; `LibrarySeriesControllerTest.should_threadAuthenticatedUserIdIntoTheService_when_adding`; `LibrarySeriesEndpointsSecurityTest.should_return201_when_addSeriesIsCalledWithAValidJwt`; `LibrarySeriesEndpointsSecurityTest.should_return401_when_addSeriesIsCalledWithoutAuthentication`; `LibrarySeriesServiceTest.should_persistSeriesLevelCatalogDataAndFullStructure_when_seriesIsAddedByAuthenticatedUser` |
| AC-2 | Stores every season and, within each season, every episode, each identified and labeled (season number, episode number, episode title where TMDB provides one) | `RestClientTmdbGatewaySeriesTest.should_fetchEpisodesForEverySeasonListedByTmdb_when_seriesHasMultipleSeasons`; `RestClientTmdbGatewaySeriesTest.should_preserveEpisodeAndSeasonNumbering_when_mappingTheStructure`; `RestClientTmdbGatewaySeriesTest.should_mapEpisodeTitleAsNull_when_tmdbOmitsEpisodeName`; `LibrarySeriesServiceTest.should_storeEverySeasonAndEpisodeFromTmdb_when_seriesHasMultipleSeasons`; `LibrarySeriesServiceTest.should_storeNullEpisodeTitle_when_tmdbOmitsTheEpisodeName`; `LibrarySeriesControllerTest.should_return201WithTheCreatedEntryIncludingSeasonsAndEpisodes_when_seriesIsAdded` (asserts nested `seasons[].episodes[]` shape); `LibrarySeriesRepositoryTest.should_roundTripASeriesWithSeasonsAndEpisodes_when_savedThenLookedUpByUserAndTmdbId`; `LibrarySeriesTablesConstraintsTest.should_rejectSecondSeasonRow_when_sameSeriesHasSameSeasonNumberTwice`; `LibrarySeriesTablesConstraintsTest.should_rejectSecondEpisodeRow_when_sameSeasonHasSameEpisodeNumberTwice`; `LibrarySeriesTablesConstraintsTest.should_acceptEpisodeRow_when_titleIsNull` |
| AC-3 | Enough series-level TMDB catalog data is stored to display the entry without a further TMDB call: at minimum TMDB id, title, first-air year, poster reference | `LibrarySeriesServiceTest.should_persistSeriesLevelCatalogDataAndFullStructure_when_seriesIsAddedByAuthenticatedUser`; `LibrarySeriesServiceTest.should_storeNullFirstAirYearAndPoster_when_tmdbOmitsThem`; `LibrarySeriesControllerTest.should_return201WithTheCreatedEntryIncludingSeasonsAndEpisodes_when_seriesIsAdded` (asserts `tmdbId`, `title`, `firstAirYear`, `posterUrl`, `addedAt`); `RestClientTmdbGatewaySeriesTest.should_mapTheSeriesLevelCatalogData_when_tmdbReturnsTheSeries`; `RestClientTmdbGatewaySeriesTest.should_returnNullPoster_when_tmdbOmitsPosterPath`; `RestClientTmdbGatewaySeriesTest.should_returnNullFirstAirYear_when_tmdbFirstAirDateIsMissingOrEmpty`; `LibrarySeriesRepositoryTest.should_roundTripASeriesWithSeasonsAndEpisodes_when_savedThenLookedUpByUserAndTmdbId`; `LibrarySeriesTablesConstraintsTest.should_acceptSeriesRow_when_firstAirYearAndPosterUrlAreNull` |
| AC-4 | On add, every episode of the series starts with status Planned, so the series and every season roll up to Planned (roll-up computation itself is STORY-012) | `LibrarySeriesServiceTest.should_setEveryEpisodeStatusToPlanned_when_seriesIsAdded`; `LibrarySeriesControllerTest.should_return201WithTheCreatedEntryIncludingSeasonsAndEpisodes_when_seriesIsAdded` (asserts `seasons[].episodes[].status == "PLANNED"`); `LibrarySeriesEndpointsSecurityTest.should_setEveryEpisodeToPlannedEndToEnd_when_seriesIsAdded`; `LibrarySeriesTablesConstraintsTest.should_rejectEpisodeRow_when_statusIsNull` |
| AC-5 | A given TMDB series can appear at most once in a single user's library; adding a series already present returns a clear, user-facing error and creates no duplicate | `LibrarySeriesServiceTest.should_throwDuplicateLibrarySeriesException_when_userAlreadyHasThatSeries`; `LibrarySeriesServiceTest.should_checkForDuplicateBeforeCallingTmdb_when_addingASeries`; `LibrarySeriesServiceTest.should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces`; `LibrarySeriesControllerTest.should_return409WithClearMessage_when_seriesAlreadyInLibrary`; `LibrarySeriesTablesConstraintsTest.should_rejectSecondSeriesRow_when_sameUserAddsSameTmdbIdTwice`; `LibrarySeriesEndpointsSecurityTest.should_persistAcrossUsersIndependently_when_twoUsersAddTheSameSeries` (third add by the same user -> 409) |
| AC-6 | The same TMDB series can independently exist in different users' libraries, each with its own progress | `LibrarySeriesServiceTest.should_addIndependentlyForEachUser_when_twoUsersAddTheSameTmdbSeries`; `LibrarySeriesTablesConstraintsTest.should_acceptSeriesRows_when_differentUsersAddTheSameTmdbId`; `LibrarySeriesRepositoryTest.should_notFindAnotherUsersSeries_when_queryingByUserId`; `LibrarySeriesEndpointsSecurityTest.should_persistAcrossUsersIndependently_when_twoUsersAddTheSameSeries` |
| AC-7 | A user can only add to, and see, series in their own library, never another user's | `LibrarySeriesControllerPrincipalConventionTest` (all three methods); `LibrarySeriesServiceTest.should_bindTheNewRowToTheCallingUserId_when_seriesIsAdded`; `LibrarySeriesRepositoryTest.should_notFindAnotherUsersSeries_when_queryingByUserId`; `LibrarySeriesEndpointsSecurityTest.should_return401_when_addSeriesIsCalledWithoutAuthentication` + `should_return401WithTheSameBodyAsTheRestOfTheApi_when_seriesIsCalledUnauthenticated`. See "Spec clarification" in the API contracts — the "and see" half is the same open isolation-guarantee treatment as story-007 AC-6; the user-facing read surface is STORY-009. |
| AC-8 | Adding a series whose TMDB id TMDB does not recognize returns a clear, user-facing error and adds nothing | `RestClientTmdbGatewaySeriesTest.should_throwTmdbSeriesNotFoundException_when_tmdbReturns404ForTheSeriesId`; `LibrarySeriesServiceTest.should_propagateTmdbSeriesNotFound_when_tmdbDoesNotRecognizeTheId` (verifies `save` is never called); `LibrarySeriesControllerTest.should_return404WithClearMessage_when_tmdbDoesNotRecognizeTheId`; `LibrarySeriesEndpointsSecurityTest.should_return404_when_addingATmdbIdTmdbDoesNotRecognize` (asserts the library tables are still empty afterwards) |
| AC-9 | Specials / season-0 episodes are handled explicitly and consistently (own grouping or excluded) | `RestClientTmdbGatewaySeriesTest.should_includeSeasonZeroAsItsOwnSeasonGrouping_when_tmdbListsSpecials`; `LibrarySeriesServiceTest.should_storeSeasonZeroAsItsOwnGrouping_when_tmdbIncludesSpecials`; `LibrarySeriesTablesConstraintsTest.should_acceptSeasonRow_when_seasonNumberIsZero`. Design decision recorded in the API contracts: stored as its own season grouping, never excluded. |

## Cross-Story Invariants

This story touches shared infrastructure: it extends the `library` package with the **first
multi-table aggregate** (three tables, two levels of FK/cascade), adds a **second `TmdbGateway`
read method** made of **two chained TMDB calls**, reuses the **shared `WatchStatus` enum**
unchanged, and adds **two new `GlobalExceptionHandler` mappings**.

| Invariant | Test(s) |
|---|---|
| Schema change — every FK column introduced by this story (`library_series.user_id`, `library_seasons.library_series_id`, `library_episodes.library_season_id`) is NOT NULL with a real foreign key; a row can never reference a non-existent parent. | `LibrarySeriesTablesConstraintsTest.should_rejectSeriesRow_when_userIdIsNull`; `...should_rejectSeriesRow_when_userIdReferencesNoUser`; `...should_rejectSeasonRow_when_seriesIdReferencesNoSeries`; `...should_rejectEpisodeRow_when_seasonIdReferencesNoSeason` |
| Schema change — AC-5 must hold even if the application check is bypassed or races: `UNIQUE (user_id, tmdb_id)` on `library_series` at the table level; AC-6 (different user, same id) stays legal | `LibrarySeriesTablesConstraintsTest.should_rejectSecondSeriesRow_when_sameUserAddsSameTmdbIdTwice`; `...should_acceptSeriesRows_when_differentUsersAddTheSameTmdbId`; `LibrarySeriesServiceTest.should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces` |
| Schema change — `library_episodes.status` is never nullable at rest; the AC-4 default is applied in the app and the column forbids a missing value | `LibrarySeriesTablesConstraintsTest.should_rejectEpisodeRow_when_statusIsNull`; `LibrarySeriesServiceTest.should_setEveryEpisodeStatusToPlanned_when_seriesIsAdded` |
| Schema change — no orphaned child rows are structurally possible regardless of which future story deletes a series (`ON DELETE CASCADE` seasons -> series, episodes -> seasons) | `LibrarySeriesTablesConstraintsTest.should_cascadeDeleteSeasonsAndEpisodes_when_aSeriesRowIsDeleted` |
| Schema change — the three new `@Entity` classes must match `V6` exactly so `spring.jpa.hibernate.ddl-auto=validate` still passes on a full context load | `LibrarySeriesRepositoryTest` and `LibrarySeriesTablesConstraintsTest` (both `@SpringBootTest`, full context + Flyway + `validate`); plus the pre-existing `StreamvaultBackendApplicationTests` context-load smoke test re-running green in the Phase 3 suite |
| Repository invariant — new finder methods return "no match" without throwing | `LibrarySeriesRepositoryTest.should_returnEmptyOptional_when_noSeriesMatchesUserAndTmdbId`; `...should_returnFalse_when_existsIsCheckedForASeriesNotInLibrary` |
| `SecurityConfig` `permitAll()` is not widened: `/api/library/series` must require auth exactly like `/api/library/movies`, and every pre-existing boundary stays unmoved | `LibrarySeriesEndpointsSecurityTest.should_return401_when_addSeriesIsCalledWithoutAuthentication`; `...should_return401WithTheSameBodyAsTheRestOfTheApi_when_seriesIsCalledUnauthenticated`; `...should_stillPermitHealthWithoutAuthentication_when_seriesRoutesAreAdded`; `...should_stillAuthenticateTheAuthMeEndpoint_when_seriesRoutesAreAdded`. No `SecurityConfig` source change is made or permitted (`/api/library/**` already covers the new route from story-007). |
| ADR-001 controller-slice auth convention holds for the new slice test | Enforced automatically by the existing `ControllerSliceTestAuthConventionTest` (source-scans every `@WebMvcTest`, now also `LibrarySeriesControllerTest` and `LibrarySeriesControllerPrincipalConventionTest`); both use `@WithMockAuthenticatedUser` + `@Import(SecurityConfig.class)` + `addFilters = false`, mirroring `LibraryMovieControllerTest`. |
| Principal is the only source of the owning user id — no controller path takes a user id from the request | `LibrarySeriesControllerPrincipalConventionTest` (all three methods) |
| `GlobalExceptionHandler` gains two `@ExceptionHandler` methods; every existing mapping (including story-007's three) is untouched | The two new mappings are covered by `LibrarySeriesControllerTest` (404 / 409) and `LibrarySeriesEndpointsSecurityTest`. Existing mappings stay covered by `LibraryMovieControllerTest`, `AccountSettingsControllerTest`, `AuthControllerGoogleTest`, and `TmdbControllerTest`, all re-running unchanged in the Phase 3 full suite. |
| `TmdbGateway` gains `series(long)`; `search` / `browse` / `movie(long)` behaviour is unchanged | `RestClientTmdbGatewaySeriesTest` covers only the new method; the story-006 `RestClientTmdbGatewayTest` and the story-007 `RestClientTmdbGatewayMovieTest` re-run unchanged in the Phase 3 suite. |
| `TmdbTitleNotFoundException` (story-007, movie-specific message) is not reused or altered for the series 404 case — a distinct `TmdbSeriesNotFoundException` is added instead | `RestClientTmdbGatewaySeriesTest.should_throwTmdbSeriesNotFoundException_when_tmdbReturns404ForTheSeriesId` asserts the series-specific type; the existing `TmdbTitleNotFoundExceptionTest` and `RestClientTmdbGatewayMovieTest` 404 case re-run unchanged, still pinning the movie wording. |
| The `com.streamvault.backend.tmdb` package stays persistence-free (story-006 AC-7, reaffirmed by story-007) | `TmdbPackageReadOnlyConventionTest.should_notReferenceAnyPersistenceApi_when_scanningTheTmdbMainSourcePackage` (unchanged, still green). Its sibling `should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` is **updated** by this story — see below. |
| Story-007's Flyway pin must acknowledge V6 without losing its guard | `TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` is amended: `containsExactly(V1..V6)` with `V6__create_library_series_tables.sql` named, and its assertion message reworded to note V6 belongs to STORY-008's series/season/episode tables. Same documented-amendment pattern story-007 used on story-006's guard; no coverage dropped. |
| `WatchStatus` (story-007) is reused, not redefined or altered | No new enum test needed; `LibrarySeriesServiceTest` and `LibrarySeriesTablesConstraintsTest` write/read the existing `PLANNED` constant directly. The existing `WatchStatusTest` re-runs unchanged. |
| JWT generation is untouched | This story changes no auth/JWT code. `JwtServiceTest` and `SecurityConfigAuthFlowTest` re-run unchanged in the Phase 3 suite. |

## API Contracts

See `story-008-api-contracts.md`.

## Test Strategy

Layers mirror story-007's established pattern, extended for the season/episode tree:

- **Value-object validation (`library/dto/AddSeriesRequestValidationTest`)** — plain Jakarta
  `Validator`, no Spring context, mirrors `AddMovieRequestValidationTest`. Pins `@NotNull` /
  `@Positive` on the sole `tmdbId` field.

- **Gateway unit tests (`tmdb/RestClientTmdbGatewaySeriesTest`)** — `MockRestServiceServer.bindTo`,
  no Spring context, extends the story-007 `RestClientTmdbGatewayMovieTest` pattern to a
  **two-stage** call sequence: one `GET /tv/{id}` expectation for series-level data, then one
  `GET /tv/{id}/season/{n}` expectation per season for episodes, registered on the same
  `MockRestServiceServer` in call order. Pins the series-level mapping, the per-season episode
  mapping, season-number and episode-number preservation, season `0` handling (AC-9), null
  `firstAirYear` / `posterUrl` / episode `title`, and the failure split: a `404` on the series call
  -> `TmdbSeriesNotFoundException`; any other non-2xx / transport error on either call ->
  `TmdbUnavailableException`. Fixed JSON literals, fully deterministic.

- **Service unit tests (`library/LibrarySeriesServiceTest`)** — Mockito, mocks
  `LibrarySeriesRepository` and `TmdbGateway`, mirrors `LibraryMovieServiceTest`. The home for the
  add algorithm: duplicate-before-TMDB ordering, per-user independence, binding the row to the
  caller's id, propagation of `TmdbSeriesNotFoundException` and `TmdbUnavailableException` with
  **no write**, the unique-constraint race backstop, null series-level field pass-through, every
  episode forced to `PLANNED` regardless of anything TMDB might send, and season/episode tree
  fidelity including season `0`.

- **Controller slice test (`library/LibrarySeriesControllerTest`)** —
  `@WebMvcTest(LibrarySeriesController.class)`, `@AutoConfigureMockMvc(addFilters = false)`,
  `@Import(SecurityConfig.class)`, `@WithMockAuthenticatedUser`, `@MockitoBean
  LibrarySeriesService`, `@MockitoBean JwtService`. Mirrors `LibraryMovieControllerTest`. Covers the
  201 body including the nested `seasons[].episodes[]` shape, the principal's id threading into the
  service, and the 400 / 404 / 409 / 502 mappings. Cannot assert 401 (filters off) — that is Layer
  2.

- **Principal convention test (`library/LibrarySeriesControllerPrincipalConventionTest`)** — mirrors
  `LibraryMovieControllerPrincipalConventionTest`. Pins that the owning user id is always
  `principal.userId()` and never taken from the request body (AC-7).

- **Persistence constraint test (`library/LibrarySeriesTablesConstraintsTest`)** — `@SpringBootTest`
  + H2 in `MODE=PostgreSQL` + `JdbcTemplate`, mirrors `LibraryMoviesTableConstraintsTest` but across
  three tables. Drives raw inserts (and one delete) to pin every schema-level invariant: the two FK
  chains, the three unique constraints, `library_episodes.status` NOT NULL, nullable series-level
  columns, season `0` accepted, and cascade delete.

- **Repository test (`library/LibrarySeriesRepositoryTest`)** — `@SpringBootTest` + H2, autowires
  `LibrarySeriesRepository` and `UserRepository`. Covers the empty-result invariant, a save -> find
  round-trip carrying the full season/episode tree (built via the entity's `addSeason` /
  `addEpisode` helpers and persisted with one cascading `save`), and that one user's rows are
  invisible to a query for another user's id (AC-7).

- **Security / end-to-end integration test (`library/LibrarySeriesEndpointsSecurityTest`)** —
  `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` (real filter chain), H2, real
  register -> login -> token, `@MockitoBean TmdbGateway` so no live TMDB call happens. Mirrors
  `LibraryEndpointsSecurityTest`, kept as a **separate class** rather than added to story-007's file
  so that file stays untouched. The AC-7 auth home and the `SecurityConfig` invariant home for the
  new route; also the full-stack home for AC-4 (every episode Planned end-to-end), AC-5 (same user
  re-add -> 409), AC-6 (two users, same id, both 201), and AC-8 (unknown id -> 404, tables still
  empty).

- **Convention test update (`tmdb/TmdbPackageReadOnlyConventionTest`)** — the Flyway pin moves from
  V1..V5 to V1..V6 with `V6__create_library_series_tables.sql` named and the message reworded. The
  persistence-token source scan of the `tmdb` package is unchanged and still passes.

### RED state expectation

`LibrarySeries`, `LibrarySeason`, `LibraryEpisode`, `LibrarySeriesRepository`,
`LibrarySeriesService`, `LibrarySeriesController`, their DTOs and exceptions, plus
`TmdbGateway.series`, `TmdbSeries`/`TmdbSeason`/`TmdbEpisode`, and `TmdbSeriesNotFoundException` do
not exist yet. The test module fails at `test-compile`. Additionally, before the amendment
`TmdbPackageReadOnlyConventionTest` would fail on the V6 migration file; after the amendment it
stays green only once Dev has actually added `V6__create_library_series_tables.sql`. That
compile/run failure is the expected Phase 1 RED, consistent with story-007.

## Out of Scope

- Setting or changing episode/season/series status after add, and the roll-up computation —
  STORY-012.
- Re-syncing a series' structure when TMDB adds seasons or episodes later — STORY-018.
- Removing a series from the library — STORY-011 (the `ON DELETE CASCADE` schema invariant is
  tested now regardless, as a structural property, not a removal feature).
- The user-facing library list / detail view — STORY-009. AC-7's "and see" is verified only as an
  isolation property, same treatment as story-007 AC-6.
- Rating, notes, watch date, streaming source — later stories add their own columns / tables.
- The exact Bean Validation default message for `tmdbId` — same pinning discipline as story-007.
- TMDB response caching across the `series(long)` call chain — allowed but not required; if Dev
  adds one, Test adds freshness regression tests in Phase 3.
- Frontend "add to library" UI — no frontend test project exists in this repo (standing gap).
- MongoDB — excluded for the entire epic per `epic-personal-library.md`.
