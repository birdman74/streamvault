# Dev Feedback — story-009 Round 2

## Summary
Needs revision. The round 1 fix is the right shape - `@Transactional(readOnly = true)` on
`LibraryService.list(...)` plus an explicit eager-fetch strategy on
`LibrarySeriesRepository.findByUserId` - but the specific `@EntityGraph` as written will not run.
This is a correctness problem with the fix itself, not a new objection to the underlying design.
Everything agreed in round 1 (the merge/filter/sort/paginate algorithm, pagination, response shape,
`WatchStatusRollup`'s derivation rule, the repeated-query-param binding confirmation) remains agreed.

## Concerns

### Concern 1: `@EntityGraph(attributePaths = {"seasons", "seasons.episodes"})` throws `MultipleBagFetchException` at query time against this project's actual entity mappings
**File**: story-009-api-contracts.md, design decision 8, `LibrarySeriesRepository.findByUserId`
**Issue**: I checked `LibrarySeries.seasons` and `LibrarySeason.episodes` directly rather than
taking the entity graph on faith. Both are declared `List<...>` `@OneToMany` collections (with
`@OrderBy("id ASC")`, no `@OrderColumn`), i.e. both are Hibernate bags, not `Set`s or indexed lists.
Requesting two nested `*ToMany` bag paths in a single `@EntityGraph`/JOIN-FETCH (`seasons` and the
nested `seasons.episodes`) is the textbook trigger for
`org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags`. This
throws when `findByUserId` actually executes a query that reaches a series with at least one
season, not at application startup, so it would surface the first time the new
`LibrarySeriesRepositoryEagerFetchTest` runs (the exact test Test added to prove this works) and the
first time any real `GET /api/library` request includes a series with seasons - which is the normal
case for AC-1/AC-2/AC-4, same blast radius as round 1's original bug. I have not run this against a
real Hibernate instance myself since the production code doesn't exist yet to compile against, but
the bag-vs-bag conflict is a well-documented Hibernate limitation independent of version, and
nothing in this codebase overrides the default collection type, so I'm confident the entity graph as
specified will not survive first contact rather than just flagging it as a risk.
**Suggestion**: Pick one explicit replacement strategy and state it in the contract. The one that
needs no entity class change (keeping `LibrarySeries`/`LibrarySeason` genuinely read-only, per this
story's own cross-story invariant) and still bounds the query count independent of library size:
split the single two-level entity graph into two separate fetches run inside the same
`@Transactional(readOnly = true)` service method so they share one session:
1. `@EntityGraph(attributePaths = {"seasons"}) List<LibrarySeries> findByUserId(Long userId)` -
   a single bag path, safe on its own.
2. A second query, run once per `list(...)` call (not once per series), that eager-fetches every
   episode for every season just loaded - e.g. `LibrarySeasonRepository` (or a method added to an
   existing repository) with `@Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH
   s.episodes WHERE s.series.id IN :seriesIds")`. Hibernate attaches the fetched `episodes`
   collections onto the already-managed `LibrarySeason` entities from step 1 by entity identity
   within the same session, so `WatchStatusRollup.seriesStatus(...)` sees both collections already
   initialized with no further round trips.
This is 2 queries total per `GET /api/library` call regardless of how many series the user has,
same asymptotic win as the original proposal, without the bag conflict. If Test has a different
preferred fix (e.g. converting one collection to `Set`, or `@Fetch(FetchMode.SUBSELECT)` /
`@BatchSize`), I don't have a strong preference between alternatives that don't touch the entity
classes - I only object to the specific `@EntityGraph` as currently written, since it won't run.
**AC coverage**: No AC changes. Same as round 1, this is about making AC-1/AC-2/AC-4 pass at runtime
rather than throw - now a different runtime exception at the same call site.

## Questions for Test
None - this is a concrete, checkable claim about this codebase's actual entity mappings
(`LibrarySeries.seasons` / `LibrarySeason.episodes` are both `List`, confirmed by reading the
entity source), not an open design question.

## Agreement
Everything from round 1 that round 1's revision didn't change is still agreed: the merge/filter/
sort/paginate algorithm, pagination defaults/ceiling, `year`/`id` unification, user-scoping, the
`WatchStatusRollup` derivation rule, the response shape, and the repeated-query-param binding
confirmation (Question 1). The `@Transactional(readOnly = true)` half of the round 1 fix is also
still agreed - only the specific `@EntityGraph` attribute paths need to change.
