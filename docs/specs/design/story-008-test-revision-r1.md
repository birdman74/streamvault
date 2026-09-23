# Test Revision — story-008 Round 1

Responds to `story-008-dev-feedback-r1.md`.

## Disposition

Dev's single concern is accepted as technically valid. Revised the gateway contract and the
gateway-level test to match; no AC or invariant coverage was dropped, and no other layer changes.

## Concern 1 — batch the TMDB episode fetch instead of one call per season

**Accepted.** Dev is right that pinning the contract to one sequential `GET /tv/{id}/season/{n}`
call per season makes request latency and failure surface scale linearly with season count, and
that this is a real cost for this domain (TMDB has plenty of 20-30+ season entries: procedurals,
soaps, long-running animated series). Dev's confirmation that TMDB's `GET /tv/{series_id}` supports
`append_to_response=season/{n1},season/{n2},...` (embedding each requested season's full object,
including its `episodes` array, in the single response) is accepted at face value — this is
documented TMDB behavior and Dev did the verification, not Test; Test's role here is judging whether
the change is sound against the AC/invariant surface, which it is.

**What changed:**

- `story-008-api-contracts.md` — the overview bullet list, the `TmdbGateway.series` interface
  javadoc, the `RestClientTmdbGateway` extended section, and the 404/502 response descriptions now
  describe the batched `append_to_response` design (series-detail call, then one batch call per
  group of up to 20 season numbers, in TMDB's listed order) instead of the N+1 sequential design.
- `story-008-test-plan.md` — the cross-story invariants intro paragraph and the
  `RestClientTmdbGatewaySeriesTest` bullet under Test Strategy now describe the batched call
  sequence. Added the new chunking test to the AC-2 coverage row.
- `RestClientTmdbGatewaySeriesTest.java` — rewritten so every test that previously registered one
  `MockRestServiceServer` expectation per season now registers one batch expectation per group of up
  to 20 season numbers, matched on the `append_to_response=season/{n1},season/{n2},...` query
  fragment. Response JSON fixtures updated to the batched TMDB response shape (`season/{n}` keyed
  objects embedded in the series-detail response body).
- **New test**: `should_batchSeasonFetchesInGroupsOfAtMost20_when_seriesHasMoreThan20Seasons` — pins
  the >20-season chunking boundary Dev's suggestion introduces. A 25-season series must produce
  exactly two batch calls (seasons 1-20, then 21-25), not one call per season and not one unbounded
  batch. This is new coverage the original per-season design didn't need, added because the batching
  strategy is itself now a behavior worth pinning, not just an implementation detail.
- Renamed two failure-path tests for clarity now that the season fetch is no longer per-season:
  `should_throwTmdbUnavailableException_when_aSeasonCallFails` ->
  `should_throwTmdbUnavailableException_when_theSeasonBatchCallFails`;
  `should_throwTmdbUnavailableException_when_aSeasonCallReturns404` ->
  `should_throwTmdbUnavailableException_when_theSeasonBatchCallReturns404`. Same assertions, same
  AC-8/502 split (a batch-call failure is still "unavailable", never "not found" — the series id was
  already confirmed on the first call).

**What did not change:** no AC or invariant coverage was dropped or narrowed — AC-2, AC-8, and AC-9
are still covered by the same set of behaviors, just exercised through a batched call shape. No
response DTO shape changed (`TmdbSeries` / `TmdbSeason` / `TmdbEpisode` are unchanged). No service,
controller, or schema layer is touched by this revision — contained entirely to
`RestClientTmdbGateway`'s internal call shape and `RestClientTmdbGatewaySeriesTest`, exactly as Dev
scoped it.

**Chunking size:** used Dev's suggested "batches of 20 in TMDB's listed order" (TMDB's own
documented cap on `append_to_response` items per call) rather than opening it as a separate
question — this is an implementation-level constant, not a spec ambiguity requiring Brian's input.

## RED state after this revision

`mvn test-compile` still fails, and still only on "cannot find symbol" for the not-yet-implemented
production classes (`LibrarySeries`, `LibrarySeason`, `LibraryEpisode`, `LibrarySeriesService`,
`TmdbGateway.series`, etc.) — confirmed by running `test-compile` after this revision. The rewritten
`RestClientTmdbGatewaySeriesTest.java` itself introduces no new compile errors beyond the same
missing `TmdbSeries` / `TmdbSeriesNotFoundException` symbols the original version also depended on.

## Next step

No further concerns from Dev on this round. If Dev agrees, the next artifact is
`story-008-agreed.md` per the workflow, triggering implementation.
