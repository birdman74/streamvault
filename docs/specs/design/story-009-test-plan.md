# Test Plan — story-009: View and Filter My Library

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | A signed-in user can retrieve a list of every item in their own library, movies and series together | `LibraryServiceTest.should_returnBothMoviesAndSeries_when_noTypeFilterIsApplied`; `LibraryControllerTest.should_return200WithTheCombinedList_when_libraryIsRequested`; `LibraryControllerTest.should_threadAuthenticatedUserIdIntoTheService_when_listing`; `LibraryListEndpointsSecurityTest.should_return200_when_libraryIsRequestedWithAValidJwt`; `LibraryListEndpointsSecurityTest.should_return401_when_libraryIsRequestedWithoutAuthentication` |
| AC-2 | Each listed item shows at minimum its title, media type, poster reference, and current status; for a series the status shown is the rolled-up status per STORY-012 | `LibraryControllerTest.should_return200WithTheCombinedList_when_libraryIsRequested` (asserts `title`, `type`, `posterUrl`, `status` on both a movie and a series item); `LibraryServiceTest.should_mapAMoviesStoredStatus_when_buildingAnItem`; `LibraryServiceTest.should_mapASeriesStatusAsItsWatchStatusRollupValue_when_buildingAnItem`; `WatchStatusRollupTest` (all methods, pins the exact STORY-012 AC-2/AC-3 rule); `LibraryListEndpointsSecurityTest.should_showTheRolledUpStatusForASeries_whenEpisodesAreAMixOfStatuses_endToEnd` |
| AC-3 | The list can be filtered to movies only, series only, or both | `LibraryServiceTest.should_returnOnlyMovies_when_typeFilterIsMovieOnly`; `...should_returnOnlySeries_when_typeFilterIsSeriesOnly`; `...should_returnBothMoviesAndSeries_when_noTypeFilterIsApplied`; `...should_notQueryTheSeriesRepositoryAtAll_when_typeFilterIsMovieOnly`; `...should_notQueryTheMovieRepositoryAtAll_when_typeFilterIsSeriesOnly`; `LibraryControllerTest.should_passRepeatedTypeParametersToTheService_when_filteringByType`; `LibraryControllerTest.should_return400_when_typeValueIsUnrecognized`; `LibraryListEndpointsSecurityTest.should_filterToMoviesOnly_whenTypeParameterIsMovie_endToEnd` |
| AC-4 | The list can be filtered by status (Planned, Currently Watching, Watched), including any combination of the three | `LibraryServiceTest.should_returnOnlyItemsMatchingASingleRequestedStatus_when_oneStatusIsRequested`; `...should_returnItemsMatchingAnyOfSeveralRequestedStatuses_when_multipleStatusesAreRequested`; `...should_returnEveryStatus_when_noStatusFilterIsApplied`; `...should_filterASeriesByItsRolledUpStatus_notByAnyRawEpisodeStatus`; `LibraryControllerTest.should_passRepeatedStatusParametersToTheService_when_filteringByStatus`; `LibraryControllerTest.should_return400_when_statusValueIsUnrecognized`; `LibraryListEndpointsSecurityTest.should_filterToWatchedOnly_whenStatusParameterIsWatched_endToEnd` |
| AC-5 | Type and status filters can be applied together and return only items matching all active filters | `LibraryServiceTest.should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested`; `LibraryListEndpointsSecurityTest.should_applyTypeAndStatusFiltersTogether_endToEnd` |
| AC-6 | The list is bounded per response (paginated or capped) so a large library never produces an unbounded response | `LibraryServiceTest.should_capTheReturnedItemsAtPageSize_when_moreItemsExistThanOnePageHolds`; `...should_computeTotalPagesFromTotalItemsAndPageSize`; `...should_returnTheSecondPageOfItems_when_pageIsTwo`; `...should_defaultPageToOneAndPageSizeToTwenty_when_bothAreOmitted`; `...should_returnAnEmptyPage_when_theRequestedPageIsBeyondTheLastPage`; `LibraryControllerTest.should_return400_when_pageIsLessThanOne`; `...should_return400_when_pageSizeIsZero`; `...should_return400_when_pageSizeExceedsOneHundred`; `LibraryListRequestValidationTest` (all methods); `LibraryListEndpointsSecurityTest.should_returnOnlyPageSizeItems_whenMoreItemsExistThanOnePageHolds_endToEnd` |
| AC-7 | A user only ever sees their own library; another user's items never appear regardless of filters | `LibraryServiceTest.should_onlyQueryRepositoriesWithTheGivenUserId_when_listing`; `LibraryControllerPrincipalConventionTest` (all methods); `LibraryMovieRepositoryTest.should_notIncludeAnotherUsersMovies_when_findingByUserId` (amended, see Cross-Story Invariants); `LibrarySeriesRepositoryTest.should_notIncludeAnotherUsersSeries_when_findingByUserId` (amended); `LibraryListEndpointsSecurityTest.should_keepTwoUsersLibrariesIsolated_endToEndRegardlessOfFilters`; `LibraryListEndpointsSecurityTest.should_return401_when_libraryIsRequestedWithoutAuthentication` |
| AC-8 | A user with an empty library, or a filter combination that matches nothing, receives an explicit empty result rather than an error | `LibraryServiceTest.should_returnAnEmptyList_when_theUserHasNoLibraryItemsAtAll`; `...should_returnAnEmptyList_when_theFilterCombinationMatchesNothing`; `...should_returnAnEmptyPage_when_theRequestedPageIsBeyondTheLastPage`; `LibraryListEndpointsSecurityTest.should_return200WithAnEmptyItemsArray_when_theLibraryIsEmpty`; `...should_return200WithAnEmptyItemsArray_when_theFilterCombinationMatchesNothing_endToEnd` |

## Cross-Story Invariants

This story touches shared infrastructure: it adds a new finder method to each of the two existing
library repositories (STORY-007, STORY-008), and it is the first place in the codebase that computes
a derived value from STORY-012's not-yet-implemented roll-up rule.

| Invariant | Test(s) |
|---|---|
| New finder methods return "no match" without throwing, including an entirely empty table | `LibraryMovieRepositoryTest.should_returnAnEmptyList_when_findingByUserIdForAUserWithNoMovies` (amended); `LibrarySeriesRepositoryTest.should_returnAnEmptyList_when_findingByUserIdForAUserWithNoSeries` (amended) |
| New finder methods are user-scoped: one user's rows never appear for another user's id | `LibraryMovieRepositoryTest.should_notIncludeAnotherUsersMovies_when_findingByUserId` (amended); `LibrarySeriesRepositoryTest.should_notIncludeAnotherUsersSeries_when_findingByUserId` (amended) |
| `SecurityConfig` `permitAll()` is not widened: `/api/library` must require auth exactly like `/api/library/movies` and `/api/library/series`, and every pre-existing boundary stays unmoved | `LibraryListEndpointsSecurityTest.should_return401_when_libraryIsRequestedWithoutAuthentication`; `...should_return401WithTheSameBodyAsTheRestOfTheApi_when_libraryIsCalledUnauthenticated`; `...should_stillPermitHealthWithoutAuthentication_when_libraryListRouteIsAdded`; `...should_stillAuthenticateTheAuthMeEndpoint_when_libraryListRouteIsAdded`. No `SecurityConfig` source change is made or permitted. |
| ADR-001 controller-slice auth convention holds for the new slice test | Enforced automatically by the existing `ControllerSliceTestAuthConventionTest` (source-scans every `@WebMvcTest`, now also picks up `LibraryControllerTest` and `LibraryControllerPrincipalConventionTest`); both use `@WithMockAuthenticatedUser` + `@Import(SecurityConfig.class)` + `addFilters = false`, mirroring `LibraryMovieControllerTest`. |
| Principal is the only source of the owning user id - no controller path takes a user id from the request | `LibraryControllerPrincipalConventionTest` (all methods). `LibraryListRequest` has no `userId` component for a client to even attempt to supply one. |
| `GlobalExceptionHandler` gains no new mapping; every existing mapping is untouched | No new exception type is introduced by this story; the full Phase 3 suite re-running every existing `*ControllerTest` / `*EndpointsSecurityTest` unchanged is the regression evidence. |
| `WatchStatus` (story-007) is reused, not redefined or altered | No new enum test needed for the enum itself; `WatchStatusRollupTest` and `LibraryServiceTest` read/write the existing three constants directly. The existing `WatchStatusTest` re-runs unchanged. |
| `WatchStatusRollup`'s derivation rule exactly matches STORY-012 AC-2/AC-3's text, so STORY-012's own design phase has a passing, named reference implementation to reuse rather than re-deriving the rule | `WatchStatusRollupTest.should_rollUpToPlanned_when_theGroupingIsEmpty`; `...should_rollUpToWatched_when_everyStatusIsWatched`; `...should_rollUpToPlanned_when_everyStatusIsPlanned`; `...should_rollUpToCurrentlyWatching_when_statusesAreAMixOfAllThreeValues`; `...should_rollUpToCurrentlyWatching_when_anyStatusIsCurrentlyWatchingEvenIfOthersAreWatched`; `...should_rollUpThroughTwoLevels_when_computingASeriesStatusFromItsSeasonsAndEpisodes` |
| `LibraryMovie` / `LibrarySeries` / `LibrarySeason` / `LibraryEpisode` entities (STORY-007, STORY-008) are read-only from this story's perspective - no setter is added, no field changes | No test needed; `LibraryServiceTest` constructs fixtures using only each entity's existing public constructor and `addSeason` / `addEpisode` helpers, identical to `LibrarySeriesServiceTest`'s existing fixture style. |
| JWT generation is untouched | This story changes no auth/JWT code. `JwtServiceTest` and `SecurityConfigAuthFlowTest` re-run unchanged in the Phase 3 suite. |
| No Flyway migration is added | `TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` re-runs **unchanged** (still pinned at V1..V6) - this story adds no table, so unlike story-007/008 this guard needs no amendment. |
| A `LibrarySeries` loaded by a repository query - not one just built in Java memory in the same call, as every existing read in this codebase does today - must have `seasons` / `seasons.episodes` already initialized, since `spring.jpa.open-in-view: false` means the Hibernate session closes the moment the repository call returns (added in response to Dev's round 1 feedback, Concerns 1/2; design decision 8) | `LibrarySeriesRepositoryEagerFetchTest.should_haveSeasonsAndEpisodesAlreadyInitialized_when_accessedAfterFindByUserIdReturns` |

## API Contracts

See `story-009-api-contracts.md`.

## Test Strategy

Layers mirror story-007/008's established pattern, adapted for a read/merge/filter/paginate
endpoint with no write path and no TMDB call:

- **Rollup unit test (`library/WatchStatusRollupTest`)** - plain JUnit, no Spring context, no
  mocks. The home for pinning STORY-012's AC-2/AC-3 derivation rule itself: empty-grouping-is-
  Planned, all-Watched, all-Planned, mixed-is-CurrentlyWatching, and the two-level
  season-then-series rollup computed directly off a real `LibrarySeries` fixture (seasons/episodes
  built via the existing `addSeason` / `addEpisode` helpers, no mocking needed since these are plain
  JPA entities usable outside a persistence context).

- **Value-object validation (`library/dto/LibraryListRequestValidationTest`)** - plain Jakarta
  `Validator`, no Spring context, mirrors `AddMovieRequestValidationTest`. Pins `@Min(1)` on `page`
  and `@Min(1) @Max(100)` on `pageSize`, and that both are optional (`null` produces no violation,
  consistent with `TmdbSearchRequest.page`'s precedent). `type` / `status` carry no Bean Validation
  annotation - an unrecognized string in either is a binding failure at the HTTP layer (Layer 1),
  not a Bean Validation concern at this layer, mirroring `TmdbBrowseRequest.list`'s precedent exactly.

- **Service unit tests (`library/LibraryServiceTest`)** - Mockito, mocks `LibraryMovieRepository`
  and `LibrarySeriesRepository`. The home for the merge/filter/sort/paginate algorithm: type
  filtering (including that the "wrong" repository is never even queried when a type is excluded,
  AC-3), status filtering including the any-of-several-statuses case (AC-4), type+status combined as
  AND (AC-5), pagination math and the beyond-last-page empty case (AC-6, AC-8), empty-library and
  no-match-at-all empty results (AC-8), strict user-scoping of both repository calls (AC-7), the
  sort order (`addedAt` desc, `type` asc, `id` asc) proven with crafted fixtures whose `addedAt`
  values deliberately collide to exercise the tie-break, and that a series' status in both the
  response and the status filter is always `WatchStatusRollup.seriesStatus(...)`, never a raw
  episode value.

- **Controller slice test (`library/LibraryControllerTest`)** - `@WebMvcTest(LibraryController.class)`,
  `@AutoConfigureMockMvc(addFilters = false)`, `@Import(SecurityConfig.class)`,
  `@WithMockAuthenticatedUser`, `@MockitoBean LibraryService`, `@MockitoBean JwtService`. Mirrors
  `LibraryMovieControllerTest`. Covers the 200 body shape (pagination envelope plus one movie item
  and one series item asserting every `LibraryItemResponse` field), the principal id threading into
  the service, repeated `type=`/`status=` query parameters reaching the service as lists, and the
  400 mappings for an unrecognized `type`/`status` value and an out-of-bounds `page`/`pageSize`.
  Cannot assert 401 (filters off) - that is Layer 2. The repeated-query-param-into-`List<Enum>`
  binding path these tests depend on (`should_bindRepeatedTypeQueryParamsIntoAList_...` and friends)
  was confirmed experimentally against this project's actual Spring Boot 3.5.16 / Spring Framework
  6.2.19 in response to Dev's round 1 Question 1 - see "Response to Dev's round 1 Question 1" in
  `story-009-api-contracts.md`. No change was needed to these tests or to `LibraryListRequest`.

- **Principal convention test (`library/LibraryControllerPrincipalConventionTest`)** - mirrors
  `LibraryMovieControllerPrincipalConventionTest`. Pins that the owning user id is always
  `principal.userId()` for a `GET` request, proven across two different principals (AC-7). There is
  no request body and no `userId` component on `LibraryListRequest` for a client to attempt to
  override, so there is nothing analogous to the "ignore a client-supplied `userId`" case story-007/
  008 test; the convention here is "a different principal always produces a different service
  call," which is the meaningful equivalent for a `GET`.

- **Repository test amendments (`library/LibraryMovieRepositoryTest`, `library/LibrarySeriesRepositoryTest`)**
  - both existing `@SpringBootTest` + H2 files gain new test methods (not new files, following the
    `TmdbPackageReadOnlyConventionTest` amendment precedent from story-007/008) for the new
    `findByUserId` finder: the empty-list-not-exception invariant, a round-trip returning every row
    belonging to the queried user, and that another user's rows are excluded (AC-7). No existing
    method in either file is changed.

- **New: `library/LibrarySeriesRepositoryEagerFetchTest`** - a dedicated `@SpringBootTest` + H2
  file, added in response to Dev's round 1 feedback (Concerns 1/2), not amended into
  `LibrarySeriesRepositoryTest`. Deliberately carries no class- or method-level `@Transactional`:
  that would keep the Hibernate session open for the whole test and silently hide the exact bug this
  test exists to catch. Each repository call (`save`, then `findByUserId`) therefore runs in its own
  self-contained transaction exactly as it would on the real `GET /api/library` request path under
  `spring.jpa.open-in-view: false`, and the single test method cleans up for itself in an `@AfterEach`
  rather than relying on test-transaction rollback. Saves a series with two seasons (one with an
  episode, one with two), calls `findByUserId`, and asserts `getSeasons()` /
  `getSeasons().get(1).getEpisodes()` are both already populated with no open session - this fails
  with an uncaught `LazyInitializationException` if `findByUserId`'s `@EntityGraph` (design decision
  8) is ever dropped or narrowed.

- **Security / end-to-end integration test (`library/LibraryListEndpointsSecurityTest`)** -
  `@SpringBootTest(webEnvironment = MOCK)` + `@AutoConfigureMockMvc` (real filter chain), H2, real
  register -> login -> token. Kept as its own class rather than added to the existing
  `LibraryEndpointsSecurityTest` (story-007, movie-add-only) so that file stays untouched, mirroring
  story-008's `LibrarySeriesEndpointsSecurityTest` precedent. Seeds real `LibraryMovie` /
  `LibrarySeries` rows directly through the autowired repositories (no TMDB mock needed - this story
  makes no TMDB call) to exercise AC-1 through AC-8 full-stack: the combined list, the rolled-up
  series status shown end-to-end from a mix of episode statuses, type-only and status-only
  filtering, type+status combined, pagination capping a library larger than one page, the empty-
  library and no-match-filter empty-array cases, and that two users' libraries stay fully isolated
  from each other across every filter combination. Also the `SecurityConfig` invariant home for the
  new route (401 without a token, `/api/health` and `/api/auth/me` unaffected).

### RED state expectation

`LibraryItemType`, `WatchStatusRollup`, `LibraryService`, `LibraryController`,
`LibraryListRequest`, `LibraryItemResponse`, `LibraryListResponse`, and the two new repository
finder methods do not exist yet. The test module fails at `test-compile`, consistent with
story-002/005/006/007/008's expected Phase 1 RED. The two amended repository test files
(`LibraryMovieRepositoryTest`, `LibrarySeriesRepositoryTest`) also fail to compile, since their new
methods call the not-yet-existing `findByUserId`, and the new
`LibrarySeriesRepositoryEagerFetchTest` fails to compile for the same reason.

## Out of Scope

- Setting or changing any watch status, including the STORY-012 bulk season/series actions -
  `WatchStatusRollup` is a read-only derivation used only for display and filtering here.
- Removing an item from the library - STORY-011.
- Free-text search, and filtering/grouping by streaming source, rating, or watch date - excluded by
  the story's own "Out of Scope" section.
- Collections or named sub-libraries - excluded by the story's own "Out of Scope" section.
- Any frontend list/filter UI - no frontend test project exists in this repo (standing gap).
- MongoDB - excluded for the entire epic per `epic-personal-library.md`.
