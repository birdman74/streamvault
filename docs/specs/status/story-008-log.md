# STORY-008 Log: Add TV Series from TMDB to Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-008-add-series-from-tmdb.md`
Branch: `feature/story-008-add-series-from-tmdb`
PR: #29

---

## Phase 3: Final PR verification — APPROVED (2026-09-23)

**Verdict: APPROVED.** All AC-1..AC-9 and every identified cross-story invariant pass. No
regressions found; no new regression tests required.

**Suite run:** `mvn clean verify` on PR #29 head (`485016a`): **301 tests, 0 failures, 0 errors, 0
skipped**, JaCoCo coverage gate met, `BUILD SUCCESS`. Matches Dev's PR-reported numbers exactly.

**AC coverage:** every row in `story-008-test-plan.md`'s AC table maps to tests that ran and passed
in this suite — `LibrarySeriesControllerTest` (7), `LibrarySeriesEndpointsSecurityTest` (8),
`LibrarySeriesServiceTest` (13), `LibrarySeriesRepositoryTest` (4),
`LibrarySeriesControllerPrincipalConventionTest` (3), `LibrarySeriesTablesConstraintsTest` (13),
`RestClientTmdbGatewaySeriesTest` (13), `RestClientTmdbGatewayBatchPartitioningTest` (6),
`AddSeriesRequestValidationTest` (5), `LibrarySeriesEntityWiringTest` (3). No AC or invariant left
uncovered.

**Regression analysis (diff-driven, per Phase 3 step 3):**
- `GlobalExceptionHandler`: additive only (two new `@ExceptionHandler` methods for
  `DuplicateLibrarySeriesException` / `TmdbSeriesNotFoundException`); every pre-existing mapping's
  test class re-ran unchanged and green (`LibraryMovieControllerTest`, `AccountSettingsControllerTest`,
  `AuthControllerGoogleTest`, `TmdbControllerTest`).
- `RestClientTmdbGateway` / `TmdbGateway`: purely additive (`series(long)` + `partitionIntoBatches`);
  `search` / `browse` / `movie(long)` untouched, confirmed by inspecting the diff (no existing method
  body changed) and by `RestClientTmdbGatewayTest` and `RestClientTmdbGatewayMovieTest` re-running
  unchanged and green.
- `TmdbPackageReadOnlyConventionTest`: the only pre-existing test file modified by this story
  (Flyway pin V1..V5 -> V1..V6). This amendment was made by Test in the Phase 1 commit, not Dev's
  implementation commit, and was already reviewed during design iteration — confirmed still correctly
  pinning the full migration list and still green.
- No changes to `SecurityConfig`, JWT generation, `WatchStatus`, `User`, `pom.xml`, or any
  configuration file. Full schema/FK/cascade/unique-constraint invariant set, principal-sourced
  user-id convention, and ADR-001 controller-slice convention all verified via the tests listed
  in `story-008-test-plan.md`'s Cross-Story Invariants table, all green.

No shared-infrastructure surface was changed beyond what the Phase 1 test plan already anticipated
and covered. No additional regression tests were written this phase.

**Recommendation:** APPROVED. Posted as a PR comment on #29 (gh review blocked for the PR's own
author account — see below) carrying this verdict.

**Note on `gh pr review`:** `gh` in this container authenticates as the same bot account that opened
PR #29, so `gh pr review --approve` is rejected by GitHub (`Review Can not approve your own pull
request`). Per persona convention, the formal verdict is posted as a structured PR comment instead;
this is not a task failure.

---

## Phase 2: Implementation complete (2026-09-23)

Implemented against `story-008-agreed.md` / the round-1-revised `story-008-api-contracts.md`. All of
Test's failing tests plus Dev's own lower-level unit tests pass; `mvn clean verify` is green
(301 tests, 0 failures/errors, JaCoCo coverage gate met).

**New production code:**
- `V6__create_library_series_tables.sql` — `library_series` / `library_seasons` / `library_episodes`,
  exactly as specified (two FK/cascade chains, three unique constraints).
- `com.streamvault.backend.library`: `LibrarySeries` (aggregate root, `addSeason`), `LibrarySeason`
  (`addEpisode`), `LibraryEpisode` — bidirectional wiring done via package-private `setLibrarySeries`
  / `setLibrarySeason` methods (private fields can't be set cross-class even within a package, so a
  package-private accessor is the mechanism behind the contract's `season.librarySeries = this`
  shorthand). Both `@OneToMany` sides use `@OrderBy("id ASC")`, not `@OrderColumn` — the schema has no
  dedicated order column, and `@OrderColumn` would have required one; `@OrderBy` generates an
  `ORDER BY` over the existing `id` column instead, which is enough to preserve TMDB's season/episode
  order on reload since rows are inserted in that order.
- `LibrarySeriesRepository`, the four `dto` records, `DuplicateLibrarySeriesException`,
  `LibrarySeriesService` (duplicate-check-before-TMDB, forces `PLANNED` on every episode regardless
  of TMDB input, unique-constraint race backstop), `LibrarySeriesController`.
- `TmdbSeries` / `TmdbSeason` / `TmdbEpisode` records, `TmdbSeriesNotFoundException`.
- `TmdbGateway.series(long)` + `RestClientTmdbGateway` implementation: one `GET /tv/{id}` call for
  series-level data, then `partitionIntoBatches` (season-number chunking at 20, made package-private
  static specifically so it's unit-testable as a pure function per the agreed design) driving one
  `GET /tv/{id}?append_to_response=season/{n1},...` call per batch. The batched response's dynamic
  `season/{n}` keys are read via Jackson `JsonNode` rather than a fixed record shape, since the key
  set varies per batch and per series.
- Two additive `GlobalExceptionHandler` mappings (`DuplicateLibrarySeriesException` -> 409,
  `TmdbSeriesNotFoundException` -> 404).

**Dev-authored unit tests (TDD, written before the corresponding production code, both failing
first):**
- `RestClientTmdbGatewayBatchPartitioningTest` — `partitionIntoBatches` in isolation, no
  `MockRestServiceServer`, covering the boundary counts the agreed design called out: 0, 1, 20, 21,
  40, 41 seasons.
- `LibrarySeriesEntityWiringTest` — `addSeason` / `addEpisode` both-sides-set invariant, directly
  rather than only incidentally through `LibrarySeriesRepositoryTest`'s round-trip.

No deviation from the agreed design's implementation plan. `TmdbPackageReadOnlyConventionTest`'s V6
pin (already amended by Test in the Phase 1 commit) passes now that `V6__create_library_series_tables.sql`
exists.

Committing implementation and opening the PR against `main` as `claude-streamvault-dev`.

---

## Phase 2: Design agreed, round 2 (2026-09-23)

Reviewed Test's round 1 revision (`story-008-test-revision-r1.md`) fresh, not just the single
concern it addressed. Confirmed the batched `append_to_response` gateway contract fully resolves
Dev's round 1 concern (N+1 sequential season calls -> 2 calls for the common <=20-season case,
`ceil(N/20)+1` worst case) with no AC or invariant coverage dropped, no response DTO shape changed,
and no service/controller/schema layer touched. Re-checked the rest of the design independently:
schema-level FK/cascade/unique constraints for AC-5/AC-6/no-orphans, the exception split
(`TmdbSeriesNotFoundException` only on the series call, `TmdbUnavailableException` on any batch-call
failure since the series id is already confirmed), season-0/null-title handling for AC-9/AC-2, and
the AC-7 "and see" isolation-guarantee treatment (consistent with the still-pending story-007 AC-6
precedent). No new concerns.

Agreed. Committed `story-008-agreed.md`. Next step: Dev begins Phase 2 implementation — lower-level
unit tests first (TDD), then implementation until Test's failing tests and Dev's own unit tests all
pass, then `mvn clean verify`, then PR.

---

## Phase 2: Design iteration round 1 (2026-09-23)

Dev's round 1 feedback (`story-008-dev-feedback-r1.md`) raised one concern, otherwise agreed the
design was sound: the gateway contract's `series(long)` sequential one-`GET /tv/{id}/season/{n}`
-call-per-season fetch scales latency and failure surface linearly with season count, a real cost
for TMDB series entries running 20-30+ seasons. Dev verified TMDB's `GET /tv/{series_id}` supports
`append_to_response=season/{n1},season/{n2},...` (up to 20 items per call), embedding each season's
full object including `episodes` in the single response.

**Accepted as technically valid, full details in `story-008-test-revision-r1.md`.** Revised
`story-008-api-contracts.md` and `story-008-test-plan.md` to describe the batched design (series
-detail call, then one `append_to_response` batch call per group of up to 20 season numbers, in
TMDB's listed order) in place of the N+1 sequential design. Rewrote
`RestClientTmdbGatewaySeriesTest.java` to match: every per-season expectation became a per-batch
expectation against the `append_to_response=season/...` query fragment, with fixture bodies updated
to the batched TMDB response shape. Added new coverage,
`should_batchSeasonFetchesInGroupsOfAtMost20_when_seriesHasMoreThan20Seasons` (a 25-season series
must produce exactly two batch calls: seasons 1-20, then 21-25) — the chunking boundary is now a
pinned behavior, not just an implementation detail. Renamed the two season-failure tests
(`...aSeasonCallFails` / `...aSeasonCallReturns404` -> `...theSeasonBatchCallFails` /
`...theSeasonBatchCallReturns404`) with identical assertions.

No AC or invariant coverage dropped: AC-2, AC-8, AC-9 are still covered by the same behaviors,
exercised through the new call shape. No response DTO shape, service, controller, or schema layer
touched — contained entirely to `RestClientTmdbGateway`'s internal call shape and its gateway-level
test, exactly as Dev scoped the concern. `mvn test-compile` re-run after the revision: still fails,
still only on "cannot find symbol" for the not-yet-implemented production classes; the rewritten
test file introduces no new compile errors of its own.

Pushed to `feature/story-008-add-series-from-tmdb`. Awaiting Dev's response — either a round 2
concern or `story-008-agreed.md` to trigger implementation.

---

## Phase 1: Test goes first

Complete on branch `feature/story-008-add-series-from-tmdb`, cut from `main` after STORY-007
merged (PR #28, commit `2f7d32b`). Test plan (`docs/specs/design/story-008-test-plan.md`) maps every
AC-1..AC-9 plus the cross-story invariants to named tests; API contracts
(`docs/specs/design/story-008-api-contracts.md`) define `POST /api/library/series`
(`AddSeriesRequest` -> `LibrarySeriesResponse`), the first multi-table library aggregate
(`library_series` / `library_seasons` / `library_episodes` via
`V6__create_library_series_tables.sql`), a new `TmdbGateway.series(long)` backed by two chained TMDB
calls (`GET /tv/{id}` then `GET /tv/{id}/season/{n}` per season), a new
`TmdbSeriesNotFoundException` distinct from story-007's movie-specific exception, and two additive
`GlobalExceptionHandler` mappings. No `SecurityConfig` change: `/api/library/series` already falls
under the existing `/api/library/**` -> `anyRequest().authenticated()` rule from story-007.

Two design decisions recorded as Test's implementation calls rather than spec ambiguities (see the
API contracts doc): (1) no `status` column on `library_series` or `library_seasons` — only
`library_episodes.status` is stored, per the story's own note that roll-up is STORY-012's job; (2)
season `0` (specials) is stored as its own season grouping, never excluded (AC-9) — TMDB already
models it as an ordinary `seasons[]` entry, so no special-case filtering is needed or added.

Nine failing test classes/files committed: `dto/AddSeriesRequestValidationTest`,
`RestClientTmdbGatewaySeriesTest` (MockRestServiceServer, two-stage call sequence — series-level
mapping, per-season episode mapping and numbering, season-0 handling, the 404-vs-outage split where
*any* season-call failure including a 404 is treated as unavailable since the series id is already
confirmed), `LibrarySeriesServiceTest` (Mockito, the add algorithm: duplicate-before-TMDB, per-user
independence, every episode forced to PLANNED, season/episode tree fidelity), `LibrarySeriesControllerTest`
and `LibrarySeriesControllerPrincipalConventionTest` (`@WebMvcTest` slice per ADR-001, asserting the
nested `seasons[].episodes[]` response shape), `LibrarySeriesRepositoryTest` and
`LibrarySeriesTablesConstraintsTest` (`@SpringBootTest` + H2 PostgreSQL mode, extended across three
tables: both FK chains, three unique constraints, `ON DELETE CASCADE`, season `0` accepted),
`LibrarySeriesEndpointsSecurityTest` (Layer 2 real filter chain, kept as its own class rather than
added to story-007's `LibraryEndpointsSecurityTest` so that file stays untouched — AC-7 auth home
plus full-stack AC-4/AC-5/AC-6/AC-8). Expected Phase 1 RED confirmed by running
`mvn test-compile`: the test module fails to compile on the unimplemented
`com.streamvault.backend.library.LibrarySeries`/`LibrarySeason`/`LibraryEpisode`/
`LibrarySeriesService`/`LibrarySeriesController` symbols and `TmdbGateway.series`, consistent with
the story-002/005/006/007 convention.

Cross-story amendment (documented, not silent): story-007's
`TmdbPackageReadOnlyConventionTest.should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented`
pinned the migration set at V1..V5. It is updated here to `containsExactly(V1..V6)` with
`V6__create_library_series_tables.sql` named and the assertion message + class Javadoc reworded to
record that V6 is STORY-008's series/season/episode tables. The persistence-token source scan in the
same class is unchanged and still green; no STORY-006/007 AC coverage is dropped.

Spec ambiguity NOT re-raised as new (same pending question as story-007): AC-7 reads "can only add
to, **and see**, series in their own library". This is the identical open question Test raised on
story-007's AC-6, still pending Brian's decision on PR #28 at the time of writing. This contract
applies the same answer for consistency — the **add path only**, with "see" treated as an isolation
guarantee (every write bound to `principal.userId()`, every repository access user-scoped,
cross-user invisibility proven by test); STORY-009 (prereq STORY-007 **and** STORY-008) owns the
read surface for both stories. Recorded in the API contracts doc under "Spec clarification already
surfaced to Brian" rather than opened as a second, separate question.

Design iteration with Dev has not started yet — this is the Phase 1 commit. Per the workflow, at
least one Dev review round is required before implementation begins.
