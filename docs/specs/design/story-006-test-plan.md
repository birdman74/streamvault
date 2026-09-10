# Test Plan — story-006: TMDB Search and Browse

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | Signed-in user enters free text and gets matching movie AND TV series results from TMDB | `TmdbControllerTest.should_return200WithMixedMovieAndSeriesResults_when_authenticatedUserSearchesWithAQuery`; `RestClientTmdbGatewayTest.should_mapMoviesAndSeriesAndDropPeople_when_tmdbMultiSearchReturnsMixedResults`; `RestClientTmdbGatewayTest.should_sendApiKeyAndQueryToTmdb_when_searchIsCalled`; `TmdbCatalogServiceTest.should_returnResultsFromTheGateway_when_searchIsPerformed` |
| AC-2 | Each result carries title, media type (movie/series), release-or-first-air year, and poster image where TMDB provides one | `TmdbControllerTest.should_includeTitleMediaTypeYearAndPoster_when_resultsAreReturned`; `RestClientTmdbGatewayTest.should_mapMoviesAndSeriesAndDropPeople_when_tmdbMultiSearchReturnsMixedResults` (title from `title`/`name`, `MOVIE`/`SERIES`, year from `release_date`/`first_air_date`); `RestClientTmdbGatewayTest.should_buildAbsolutePosterUrl_when_tmdbProvidesAPosterPath`; `RestClientTmdbGatewayTest.should_returnNullPoster_when_tmdbOmitsPosterPath`; `RestClientTmdbGatewayTest.should_returnNullYear_when_tmdbDateIsMissingOrEmpty` |
| AC-3 | User can browse at least one curated list with no query, returning the same result shape as search | `TmdbControllerTest.should_return200WithResults_when_authenticatedUserBrowsesWithoutAQuery`; `TmdbControllerTest.should_forwardOmittedListAsNullToTheService_when_browsing`; `TmdbControllerTest.should_browseTrendingList_when_listParamIsTrending`; `TmdbControllerTest.should_returnSameResultShapeForBrowseAsForSearch_when_bothAreCalled`; `RestClientTmdbGatewayTest.should_requestWeeklyTrendingEndpoint_when_browseListIsPopular`; `RestClientTmdbGatewayTest.should_requestDailyTrendingEndpoint_when_browseListIsTrending`; `RestClientTmdbGatewayTest.should_mapBrowseResponseToTheSameProjectionAsSearch_when_browsing`; `TmdbCatalogServiceTest.should_requestPopularList_when_browseListIsNull` (the service owns the POPULAR default) |
| AC-4 | Search and browse are paginated / bounded — a single response is never an unbounded set | `TmdbControllerTest.should_return200WithBoundedPageEnvelope_when_searchResultsArePaginated`; `TmdbControllerTest.should_passRequestedPageThroughToTheService_when_pageParamIsProvided`; `TmdbControllerTest.should_return400_when_pageParamIsLessThanOne`; `TmdbControllerTest.should_return400_when_pageParamExceedsTmdbMaximum`; `RestClientTmdbGatewayTest.should_carryTmdbPaginationMetadata_when_mappingASearchResponse`; `TmdbSearchRequestValidationTest.should_rejectRequest_when_pageIsZero`; `TmdbSearchRequestValidationTest.should_rejectRequest_when_pageExceeds500`; `TmdbCatalogServiceTest.should_defaultToPageOne_when_noPageIsSpecified` |
| AC-5 | A query matching nothing returns an explicit empty result, not an error | `TmdbControllerTest.should_return200WithEmptyResults_when_queryMatchesNothing`; `RestClientTmdbGatewayTest.should_returnExplicitEmptyPage_when_tmdbReturnsZeroResults`; `TmdbCatalogServiceTest.should_returnEmptyPageWithoutError_when_gatewayReturnsNoResults` |
| AC-6 | When TMDB is unreachable or errors, the user sees a clear message and the rest of the app keeps working | `TmdbControllerTest.should_return502WithUserFacingMessage_when_tmdbIsUnavailableDuringSearch`; `TmdbControllerTest.should_return502WithUserFacingMessage_when_tmdbIsUnavailableDuringBrowse`; `TmdbControllerTest.should_stillServeAValidRequest_afterAPriorRequestHitATmdbOutage`; `RestClientTmdbGatewayTest.should_throwTmdbUnavailableException_when_tmdbReturnsServerError`; `RestClientTmdbGatewayTest.should_throwTmdbUnavailableException_when_tmdbConnectionFails`; `RestClientTmdbGatewayTest.should_throwTmdbUnavailableException_when_tmdbReturnsUnauthorizedForABadKey`; `TmdbCatalogServiceTest.should_propagateTmdbUnavailableException_when_gatewayFails` |
| AC-7 | Search and browse are read-only against TMDB; this story writes nothing to any user's library | `TmdbPackageReadOnlyConventionTest.should_notReferenceAnyPersistenceApi_when_scanningTheTmdbMainSourcePackage`; `TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented`; `TmdbCatalogServiceTest.should_touchNothingButTheGateway_when_searchAndBrowseAreInvoked` |
| AC-8 | Only signed-in users can search or browse; unauthenticated requests are rejected consistently with the rest of the API | `TmdbEndpointsSecurityTest.should_return401_when_searchIsCalledWithoutAuthentication`; `TmdbEndpointsSecurityTest.should_return401_when_browseIsCalledWithoutAuthentication`; `TmdbEndpointsSecurityTest.should_return401WithTheSameBodyAsTheRestOfTheApi_when_tmdbIsCalledUnauthenticated`; `TmdbEndpointsSecurityTest.should_return200_when_searchIsCalledWithAValidJwt` |

## Cross-Story Invariants

| Invariant | Test(s) |
|---|---|
| `SecurityConfig`'s `permitAll()` set is not widened by this story: the new `/api/tmdb/**` routes must require auth, and every pre-existing `permitAll` route must still be reachable unauthenticated | `TmdbEndpointsSecurityTest.should_return401_when_searchIsCalledWithoutAuthentication` + `..._when_browseIsCalledWithoutAuthentication` (new routes are authenticated through the real filter chain); `TmdbEndpointsSecurityTest.should_stillPermitHealthWithoutAuthentication_when_tmdbRoutesAreAdded` (pre-existing `permitAll` route unaffected); `TmdbEndpointsSecurityTest.should_stillAuthenticateTheAuthMeEndpoint_when_tmdbRoutesAreAdded` (pre-existing authenticated route unaffected). This is Layer 2 per ADR-001. |
| Unauthenticated rejection uses the exact existing entry-point body, not a new one | `TmdbEndpointsSecurityTest.should_return401WithTheSameBodyAsTheRestOfTheApi_when_tmdbIsCalledUnauthenticated` asserts `{"error":"Authentication required"}` byte-for-byte |
| Adding a `@ExceptionHandler(TmdbUnavailableException.class)` to `GlobalExceptionHandler` must not alter any existing mapping | No new dedicated test. `GlobalExceptionHandler` has no standalone test today; its existing mappings are covered by `AccountSettingsControllerTest` (validation -> 400, `InvalidRatingTypeException` -> 400) and the `AuthController*` tests (`GoogleSignInException` -> 401, collisions -> 409, generic -> 500), all of which re-run unchanged in the Phase 3 full suite. The new mapping is purely additive and is covered by the two `TmdbControllerTest` 502 tests. Same reasoning story-005 used for `UserTableConstraintsTest`. |
| New outbound `RestClient` usage must fail safe — an upstream TMDB 4xx/5xx or transport error must never surface to the client as a raw 500 or leak upstream detail | `RestClientTmdbGatewayTest.should_throwTmdbUnavailableException_*` (three transport/status cases) plus `TmdbControllerTest.should_return502WithUserFacingMessage_*` (mapped, fixed body) |
| ADR-001 controller-slice auth convention holds for the new controller test | Enforced automatically by the existing `ControllerSliceTestAuthConventionTest`, which source-scans every `@WebMvcTest` and will now also scan `TmdbControllerTest`. `TmdbControllerTest` uses `@WithMockAuthenticatedUser` + `@Import(SecurityConfig.class)` + `addFilters = false`, matching `AccountSettingsControllerTest`. |
| No database schema change — the "nullable security-sensitive column needs app-level enforcement" and "new finder methods handle empty results" invariants from the persona checklist are N/A | This story adds no Flyway migration, no entity, no repository, no repository method. `TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented` pins the migration count so a later edit cannot quietly introduce persistence under this feature. |
| JWT generation is untouched | This story changes no auth/JWT code. `JwtServiceTest` and `SecurityConfigAuthFlowTest` re-run unchanged in the Phase 3 full suite. |

## API Contracts

See `story-006-api-contracts.md`.

## Test Strategy

Four layers, mirroring existing codebase patterns:

- **Value-object validation (`TmdbSearchRequestValidationTest`)** — plain Jakarta `Validator`,
  no Spring context, mirrors `RegisterRequestValidationTest`. Pins `@NotBlank` on `query` and the
  `@Min(1)` / `@Max(500)` bounds on `page`, including that a `null` page is accepted (optional).

- **Service unit tests (`TmdbCatalogServiceTest`)** — Mockito, mocks `TmdbGateway`, mirrors
  `AuthServiceTest` / `AccountSettingsServiceTest`. Covers default application (page -> 1, list ->
  `POPULAR`), pass-through of results and of the empty page, exception propagation, and that the
  service collaborates with **nothing but** the gateway (AC-7).

- **Gateway unit tests (`RestClientTmdbGatewayTest`)** — `MockRestServiceServer.bindTo(builder)`,
  no Spring context, a direct copy of the `GoogleTokenInfoVerifierTest` pattern. This is where the
  TMDB-JSON -> `TmdbResultPage` mapping is nailed down: mixed `movie`/`tv`/`person` payloads,
  person filtering, title/year field selection, poster URL construction, null year, pagination
  envelope pass-through, the zero-results payload, the browse endpoint routing (`/trending/all/day`
  vs `/trending/all/week`), and the three failure modes (5xx, transport error, 401 for a bad key)
  all wrapping to `TmdbUnavailableException`. Uses fixed JSON string literals — fully
  deterministic, no live network.

- **Controller slice test (`TmdbControllerTest`)** — `@WebMvcTest(TmdbController.class)`,
  `@AutoConfigureMockMvc(addFilters = false)`, `@Import(SecurityConfig.class)`,
  `@WithMockAuthenticatedUser`, `@MockitoBean TmdbCatalogService` and `@MockitoBean JwtService`.
  Mirrors `AccountSettingsControllerTest` exactly (per ADR-001). Covers request binding and
  validation status codes, the response envelope, page pass-through to the service, the empty
  result, and the 502 mapping. It cannot assert 401 (filters are off) — that is Layer 2.

- **Security integration test (`TmdbEndpointsSecurityTest`)** — `@SpringBootTest(webEnvironment =
  MOCK)` + `@AutoConfigureMockMvc` (real filter chain), H2 + JWT + tmdb props via
  `@DynamicPropertySource`, `@MockitoBean TmdbGateway` so no live TMDB call happens. Mirrors
  `SecurityConfigAuthFlowTest`. This is the AC-8 home and the `SecurityConfig` invariant home:
  real register -> login -> token round-trip, 401 body assertion, and re-checks that `/api/health`
  and `/api/auth/me` boundaries did not move.

- **Read-only convention test (`TmdbPackageReadOnlyConventionTest`)** — source/filesystem
  scanning, no Spring context, mirrors `ControllerSliceTestAuthConventionTest`. Asserts the
  `com.streamvault.backend.tmdb` main package exists and contains no persistence-API tokens
  (`jakarta.persistence`, `org.springframework.data`, `@Transactional`, `EntityManager`,
  `JdbcTemplate`, `MongoTemplate`), and that `src/main/resources/db/migration` still holds exactly
  the four V1..V4 migrations. Deterministic, holds no state between runs. This is the durable
  guard for AC-7.

### RED state expectation

None of `TmdbController`, `TmdbCatalogService`, `TmdbGateway`, `RestClientTmdbGateway`,
`TmdbMediaType`, `TmdbBrowseList`, `TmdbResult`, `TmdbResultPage`, `TmdbSearchRequest`,
`TmdbBrowseRequest`, or `TmdbUnavailableException` exists yet. The test module will fail at
`test-compile`, and `TmdbPackageReadOnlyConventionTest` fails on the missing package. That is the
expected Phase 1 RED, consistent with story-002 and story-005.

## Out of Scope

- Any write to a library — STORY-007 (movies) and STORY-008 (series). This story is proven
  write-free (AC-7), not tested for a write path that does not exist.
- Season/episode data in results — STORY-008.
- Genre filters, "because you watched", personalized ranking — story Out of Scope.
- Non-TMDB catalog sources.
- Frontend catalog UI and the "powered by TMDB" attribution placement — no frontend test
  project exists in this repo (same accepted gap as story-005). The attribution is explicitly
  not an acceptance criterion per the story notes.
- Response caching — allowed but not required by the story and not implemented against, so not
  tested. If Dev adds one, Test will add freshness/coherence regression tests in Phase 3.
- Exact Bean Validation default message text for `page` / `list` range violations — tests pin
  the 400 status and the offending field key, not framework-owned wording.
- TMDB rate-limit (429) handling beyond "it maps to 502 like any other non-2xx" — no dedicated
  backoff behavior is in scope.
