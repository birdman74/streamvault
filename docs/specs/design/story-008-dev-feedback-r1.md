# Dev Feedback — story-008 Round 1

## Summary
Needs revision. One concern, otherwise agree.

The overall design is sound and follows story-007's established patterns closely: entity/DTO
shape, exception split, unique-constraint race backstop, and the duplicate-before-TMDB ordering
all check out against the current codebase (`LibraryMovie`, `LibraryMovieService`,
`RestClientTmdbGateway`, `GlobalExceptionHandler`). Schema (`V6`) correctly puts FK + cascade +
unique constraints at the table level so AC-5/AC-6 and the no-orphan invariant hold even if the
app-level check is bypassed. The season-0 and null-title handling for AC-9/AC-2 are simple and
correct. I have one concern on the TMDB call shape before I start implementation.

## Concerns

### Concern 1: Sequential one-call-per-season TMDB fetch should use `append_to_response` instead

**File**: story-008-api-contracts.md (`RestClientTmdbGateway` extended section, and the mirrored
`TmdbGateway.series` javadoc contract)

**Issue**: The contract has `series(long)` making `GET /tv/{id}` followed by one sequential
`GET /tv/{id}/season/{n}` call per season TMDB lists. For a show with N seasons that's N+1
blocking round trips on the request thread for a single `POST /api/library/series` call. This
isn't hypothetical scale for this domain — plenty of real TMDB series entries run 20-30+ seasons
(procedurals, soaps, long-running animated series), so this design has requests that make 20+
sequential upstream calls before the DB write even starts. Two concrete costs: (1) latency/timeout
risk on the request thread scales linearly with season count, and (2) each additional call is
another independent failure point — a late-season call failing after 15 successful calls wastes
all 15 and still surfaces as a 502 to the user, per the contract's own "any season-call failure is
treated as unavailable" rule.

I confirmed TMDB's `GET /tv/{series_id}` endpoint supports `append_to_response` with `season/{n}`
values — `append_to_response=season/1,season/2,...` returns the full season objects, including
their `episodes` arrays, embedded in the single series-details response. This is documented TMDB
behavior (the endpoint's `append_to_response` parameter accepts up to 20 comma-separated items per
call).

**Suggestion**: Change the gateway contract to build the season list from the summary
`seasons[]` TMDB returns on `GET /tv/{id}`, then fetch episode detail via
`GET /tv/{id}?append_to_response=season/{n1},season/{n2},...` in batches of up to 20 season
numbers, instead of one `GET /tv/{id}/season/{n}` per season. For the common case (a show with
<=20 seasons, which is nearly everything) this collapses the whole fetch to exactly 2 calls total
(one to list season numbers, one to fetch all their episodes) instead of N+1. For the rare >20-season
outlier it still caps the call count at ceil(N/20)+1 instead of N+1.

This changes the `RestClientTmdbGatewaySeriesTest` call-sequence expectations (currently pinned as
"one `GET /tv/{id}/season/{n}` expectation per season, registered in call order" per the test
plan's Test Strategy section) to pin a batched `append_to_response` request instead. It does not
change any response DTO shape, any AC, or the service/controller/schema layers — this is contained
entirely to `RestClientTmdbGateway`'s internal call shape and its gateway-level test.

**AC coverage**: No AC coverage changes. AC-2 (full season/episode structure), AC-8 (404 on
unknown series id), and AC-9 (season 0 handled) are all still satisfied — season 0 is just another
value in the `append_to_response` batch, and a TMDB 404 on the initial `GET /tv/{id}` call is
unaffected by this change since that call still happens first, alone.

## Questions for Test
None — this is a single implementation-level concern, not a spec ambiguity. Happy to discuss
batch-size handling for the >20-season case if you want a different chunking strategy than
"batches of 20 in TMDB's listed order."
