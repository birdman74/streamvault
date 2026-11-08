# Dev Feedback — story-009 Round 1

## Summary
Needs revision. The overall shape (one read-only `LibraryService` merging two `findByUserId`
queries in memory, `WatchStatusRollup` as a pure utility, pagination/sort as specified) is sound
and I agree with it. Two concerns below are about runtime correctness given this codebase's actual
JPA configuration, not about the AC mapping or the merge/filter/paginate algorithm itself, which I
have no objection to. A third is a verification request, not a design objection.

## Concerns

### Concern 1: `WatchStatusRollup.seriesStatus(series)` will throw `LazyInitializationException` against this project's actual JPA configuration
**File**: story-009-api-contracts.md, `LibraryService` algorithm step 5, `WatchStatusRollup.seriesStatus`
**Issue**: `application.yml` sets `spring.jpa.open-in-view: false` (confirmed in
`backend/src/main/resources/application.yml`). `LibrarySeries.seasons` and `LibrarySeason.episodes`
are both default-`LAZY` `@OneToMany` (confirmed in `LibrarySeries.java` / `LibrarySeason.java` - no
explicit `fetch` or `@EntityGraph`). Every existing place in the codebase that reads `getSeasons()`/
`getEpisodes()` (`LibrarySeriesService`, mapping to `LibrarySeriesResponse`) does so on an entity
graph that was just built in Java memory via `addSeason`/`addEpisode` inside the same add-series
call, never on a `LibrarySeries` freshly loaded from the database. STORY-009 is the first place in
this codebase that loads a `LibrarySeries` via a repository query (`findByUserId`) and then walks
into `seasons` and `seasons.episodes` afterward. With `open-in-view: false` and no `@Transactional`
specified anywhere in the `LibraryService` contract, the Hibernate session used by
`librarySeriesRepository.findByUserId(userId)` closes when that repository call returns, so
`series.getSeasons()` and `season.getEpisodes()` inside `WatchStatusRollup.seriesStatus(...)` will
hit uninitialized proxies with no session - a `LazyInitializationException`, uncaught, mapped to the
generic `Exception.class` handler (likely a 500). This would break every request that includes a
series, i.e. the normal case for AC-1, AC-2, and AC-4, not an edge case.
**Suggestion**: Pick one explicit strategy and state it in the contract: either (a) mark
`LibraryService.list(...)` `@Transactional(readOnly = true)` and add an `@EntityGraph` (or a `JOIN
FETCH` JPQL query) on `LibrarySeriesRepository.findByUserId` that eagerly loads `seasons` and
`seasons.episodes` in one round trip, or (b) some equivalent that guarantees the collections are
initialized before the repository call's session closes. Either is fine with me; I don't have a
preference, but the contract currently specifies neither, and the default Spring Data behavior
under this project's actual `open-in-view: false` setting will not work.
**AC coverage**: No AC changes. This is purely about making AC-1/AC-2/AC-4 actually pass at runtime
rather than throw.

### Concern 2: once Concern 1 is fixed, the fetch strategy chosen should also avoid N+1 per series on every request
**File**: story-009-api-contracts.md, `LibraryMovieRepository` / `LibrarySeriesRepository` NEW
finder methods
**Issue**: Design decision 3 requires every series' rolled-up status to be computed for display
*and* for status filtering, which under algorithm step 5/6 means `WatchStatusRollup.seriesStatus`
runs over every series row returned for the user, not just the page being returned. If Concern 1 is
resolved by, e.g., simply adding `@Transactional` without also addressing fetch strategy, each
series will trigger one query for its seasons and then one query per season for its episodes on
every single call to `GET /api/library` - fine for a handful of series, but worth deciding
deliberately rather than discovering it later, since the contract is explicit about similar
trade-offs elsewhere (design decision 2 explicitly accepts in-memory merge over a SQL `UNION` "for
this project's personal-use scale" - this should get the same explicit treatment).
**Suggestion**: Fold this into whichever fetch strategy addresses Concern 1 - a single
`@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})` on `findByUserId` resolves both
concerns in one query per call. If Test prefers to accept the N+1 at this project's scale
deliberately instead, that's fine too - just say so explicitly the way decision 2 does, so it isn't
an accidental gap.
**AC coverage**: No AC changes.

## Questions for Test

### Question 1: has the `List<LibraryItemType>` / `List<WatchStatus>` repeated-query-param binding onto an immutable record constructor actually been exercised in this codebase?
**File**: story-009-api-contracts.md, "Request" table and `LibraryListRequest`
**Context**: The contract states the unrecognized-value-is-a-400 behavior "mirrors
`TmdbBrowseRequest.list`'s precedent exactly." I checked `TmdbBrowseRequest` and `TmdbSearchRequest`
- both only ever bind a *single* scalar value (one `Integer`, one `String`, one `TmdbBrowseList`
enum) into a record constructor parameter. Neither is an example of binding *repeated* query
parameters (`?type=MOVIE&type=SERIES`) into a `List<Enum>` record constructor parameter, which is
a different Spring MVC binding path (collection-valued constructor argument resolution) that has no
existing example anywhere in this codebase today. I believe Spring 6.1+ record/data-class binding
does support this, but since it's new ground here and the AC-3/AC-4 400 tests
(`LibraryControllerTest.should_return400_when_typeValueIsUnrecognized` /
`...should_return400_when_statusValueIsUnrecognized`) depend on a bad element inside the list still
routing through the existing `MethodArgumentNotValidException` handler rather than, say, throwing a
raw `MethodArgumentTypeMismatchException` or similar that bypasses it, I'd like this confirmed
before I rely on it rather than finding out during implementation. Not a blocking objection, just
asking for the confirmation/citation, or a fallback plan (e.g. binding `List<String>` and converting
manually in the controller/service) if it turns out not to work cleanly.

## Agreement
Everything else - the merge/filter/sort/paginate algorithm, the pagination defaults and ceiling, the
`year`/`id` unification decisions, the user-scoping approach, the `WatchStatusRollup` derivation
rule itself, the response shape, and the decision to keep `WatchStatusRollup` reusable for
STORY-012 - is agreed as specified. No concerns about `SecurityConfig`, ADR-001 compliance, or the
`GlobalExceptionHandler` non-change.
