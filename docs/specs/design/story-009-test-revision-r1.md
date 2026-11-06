# Test Revision — story-009 Round 1

Responds to `story-009-dev-feedback-r1.md`.

## Disposition

Both concerns accepted as technically valid; the question answered experimentally rather than by
citation. No AC or invariant coverage was dropped. The merge/filter/sort/paginate algorithm, the
pagination defaults and ceiling, the `year`/`id` unification decisions, the user-scoping approach,
the `WatchStatusRollup` derivation rule, the response shape, and the decision to keep
`WatchStatusRollup` reusable for STORY-012 are unchanged - Dev agreed to all of that as specified.

## Concern 1 — `WatchStatusRollup.seriesStatus(series)` would throw `LazyInitializationException`

**Accepted.** Dev is right: `backend/src/main/resources/application.yml` sets
`spring.jpa.open-in-view: false`, `LibrarySeries.seasons` / `LibrarySeason.episodes` are both
default-`LAZY`, and STORY-009 is the first place in this codebase that loads a `LibrarySeries` via a
repository query and then walks into its seasons/episodes afterward - every existing read of those
collections today walks an entity graph just built in Java memory within the same call. Without an
explicit fetch strategy, the Hibernate session used by `findByUserId` would close before
`WatchStatusRollup.seriesStatus(...)` ran, throwing on every request that includes a series - the
normal case for AC-1, AC-2, AC-4, not an edge case.

## Concern 2 — the fetch strategy should also avoid N+1 per series

**Accepted**, and folded into the same fix as Concern 1, per Dev's own suggestion.

## What changed (Concerns 1 and 2)

- `story-009-api-contracts.md` — new design decision 8: `LibrarySeriesRepository.findByUserId`
  carries `@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})`, and
  `LibraryService.list(...)` is `@Transactional(readOnly = true)`. One query per call, both
  concerns resolved together, exactly as Dev's suggestion proposed. The `LibrarySeriesRepository`
  and `LibraryService` contract code blocks, and algorithm step 5, were updated to show this.
- `story-009-test-plan.md` — new cross-story invariant row ("a `LibrarySeries` loaded by a
  repository query must have `seasons`/`seasons.episodes` already initialized, since
  `open-in-view: false` closes the session when the call returns") plus a new Test Strategy bullet
  describing the new test class below.
- **New test file**: `library/LibrarySeriesRepositoryEagerFetchTest`
  (`should_haveSeasonsAndEpisodesAlreadyInitialized_when_accessedAfterFindByUserIdReturns`) - a
  dedicated `@SpringBootTest` + H2 class, not amended into the existing
  `LibrarySeriesRepositoryTest`, because it needs the opposite transactional treatment: no
  class- or method-level `@Transactional`, so each repository call runs in its own self-contained
  transaction exactly like the real `GET /api/library` request path, instead of inside one
  long-lived test transaction that would keep the Hibernate session open and silently hide the exact
  bug this test exists to catch. (The existing `LibrarySeriesRepositoryTest` is `@Transactional` at
  the class level for an unrelated, legitimate reason - rolling back its fixed-email user seed
  between methods - which is exactly why this needed to be a separate file rather than a method
  added there.) Cleans up via `@AfterEach` instead of relying on test-transaction rollback. Confirmed
  this new file compiles against the real, already-implemented `LibrarySeries` / `LibrarySeason` /
  `LibraryEpisode` / `User` entities from story-007/008, isolated from the rest of the still-RED
  `library` package, and fails only on the not-yet-existing `findByUserId` symbol - the same
  expected RED state as everything else in this story, not a new, unrelated compile problem.

**What did not change:** no AC row changed - both concerns are, in Dev's own words, "about runtime
correctness... not an edge case," not new acceptance-criterion behavior. `WatchStatusRollup` itself,
the `LibraryService` merge/filter/sort/paginate algorithm, and every DTO shape are untouched.

## Question 1 — does repeated-query-param-into-`List<Enum>` binding work and still 400 correctly?

**Confirmed experimentally**, not resolved by citation. Dev was right that `TmdbBrowseRequest` /
`TmdbSearchRequest` only ever bind a single scalar value and are not real precedent for a
list-valued constructor parameter. Rather than assert this from framework knowledge, built a
throwaway `@WebMvcTest` spike against this project's actual dependency set (Spring Boot 3.5.16 /
Spring Framework 6.2.19 - confirmed via `mvn dependency:tree`): a scratch top-level `@RestController`
plus `record SpikeRequest(List<SpikeType> type)`, no production code touched. Result:

- `?type=MOVIE&type=SERIES` binds correctly onto a `List<Enum>` record constructor parameter.
- `?type=MOVIE&type=BOGUS` still routes through `MethodArgumentNotValidException` (auto-detected by
  `@WebMvcTest` as it always is for this codebase's `@RestControllerAdvice`), not a raw
  `MethodArgumentTypeMismatchException` that would bypass the existing handler and produce a bare
  500 instead of the shared `fields` envelope.

The spike files were deleted before this revision was committed - nothing from the spike is part of
this story's deliverable. `LibraryListRequest`, `LibraryControllerTest`'s existing
`should_bindRepeatedTypeQueryParamsIntoAList_...` / `should_return400_when_typeIsNotARecognizedConstant`
tests, and the other already-committed Phase 1 tests exercising this path are unchanged - no fallback
(e.g. binding `List<String>` and converting manually) is needed. `story-009-api-contracts.md` gained
a new "Response to Dev's round 1 Question 1" section recording this.

## RED state after this revision

`mvn test-compile` still fails, and still only on "cannot find symbol" for the not-yet-implemented
production classes/methods (`LibraryItemType`, `WatchStatusRollup`, `LibraryService`,
`LibraryController`, `LibraryListRequest`, `LibraryItemResponse`, `LibraryListResponse`, and both
repositories' `findByUserId`) - confirmed by isolating each new/changed test file against the real,
already-implemented story-007/008 production classes and running `test-compile` on it alone. The new
`LibrarySeriesRepositoryEagerFetchTest` introduces no compile error beyond the same missing
`findByUserId` symbol every other `library` test already depends on.

## Next step

Awaiting Dev's response to this revision. If Dev agrees, the next artifact is
`story-009-agreed.md` per the workflow, triggering implementation.
