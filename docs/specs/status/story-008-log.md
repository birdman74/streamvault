# STORY-008 Log: Add TV Series from TMDB to Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-008-add-series-from-tmdb.md`
Branch: `feature/story-008-add-series-from-tmdb`
PR: none yet

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
