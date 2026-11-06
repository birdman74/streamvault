# STORY-009 Log: View and Filter My Library

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-009-view-filter-library.md`
Branch: `feature/story-009-view-filter-library`
PR: none yet

---

## Phase 1: Test goes first

Complete on branch `feature/story-009-view-filter-library`, cut from `main` after STORY-008
merged (PR #29, commit `4ee0dc5`). Test plan (`docs/specs/design/story-009-test-plan.md`) maps every
AC-1..AC-8 plus the cross-story invariants to named tests; API contracts
(`docs/specs/design/story-009-api-contracts.md`) define the first read path over the library,
`GET /api/library`, which merges `library_movies` (STORY-007) and `library_series` (STORY-008) into
one bounded, paginated, filterable list for the authenticated user. This story adds no table and no
Flyway migration - purely additive read-only code over the existing schema. No `SecurityConfig`
change: `/api/library` already falls under the existing `anyRequest().authenticated()` rule.

Seven design decisions recorded as Test's implementation calls rather than spec ambiguities (see
the API contracts doc): (1) default sort is `addedAt` descending, tie-broken by `type` ascending
then `id` ascending, since a merged list from two independent primary-key sequences needs a fully
deterministic tie-break for stable pagination; (2) filtering/sorting/pagination happen in memory in
a new `LibraryService` over each user's own rows (`findByUserId` on each repository), not a
cross-table SQL `UNION` - a reasonable trade at personal-library scale; (3) a series' `status` for
display and filtering is always the `WatchStatusRollup`-derived value, never a stored column, since
`library_series`/`library_seasons` carry no status column by story-008's own design; (4) pagination
is `page`/`pageSize` query params defaulting to `1`/`20`, `pageSize` capped at `100`, mirroring the
existing `TmdbSearchRequest`/`TmdbBrowseRequest` nullable-Integer-with-service-side-default
convention; (5) a `page` beyond the last page returns `200` with an empty list, the same treatment
as a no-match filter; (6) `year` is the unified response field name for a movie's `releaseYear` or a
series' `firstAirYear`; (7) `id` is unique only combined with `type`, since `library_movies.id` and
`library_series.id` are independent sequences that can collide.

Nine failing test files committed: `WatchStatusRollupTest` (plain JUnit, pins the exact STORY-012
AC-2/AC-3 derivation rule - Watched when every child is Watched, Planned when every child is
Planned, Currently Watching otherwise, empty grouping treated as Planned - including the two-level
series-from-seasons-from-episodes case), `dto/LibraryListRequestValidationTest` (plain Jakarta
Validator, pins `@Min(1)` on `page` and `@Min(1)/@Max(100)` on `pageSize`, both optional), `LibraryServiceTest`
(Mockito, mocks both repositories; covers every AC-1 through AC-8 permutation - combined list, type
filter including that the excluded repository is never even queried, status filter including the
any-of-several case, series filtered by rolled-up status never raw episode status, type+status AND
combination, pagination math and the beyond-last-page empty case, strict user-scoping, empty
library, empty filter match, and the full sort-order/tie-break rule; uses
`ReflectionTestUtils.setField` to control `addedAt`/`id` on fixtures since both entities are
getters-only with `addedAt` stamped internally at construction - a new technique in this test suite,
but a standard Spring Test utility, used only in test code), `LibraryControllerTest` and
`LibraryControllerPrincipalConventionTest` (`@WebMvcTest` slice per ADR-001, repeated `type=`/
`status=` query-param binding, 400 for an unrecognized enum value or out-of-bounds `page`/
`pageSize`), `LibraryListEndpointsSecurityTest` (Layer 2 real filter chain and H2, kept as its own
class rather than added to `LibraryEndpointsSecurityTest` so that file stays untouched - the AC-7
auth and two-user isolation home, plus full-stack AC-1/AC-3/AC-4/AC-5/AC-6/AC-8). `LibraryMovieRepositoryTest`
and `LibrarySeriesRepositoryTest` amended (new test methods, not new files) for the new
`findByUserId` finder on each repository - empty-result-without-throwing, round-trip-returns-all-of-
one-user's-rows, cross-user-exclusion - the cross-story repository invariant. Expected Phase 1 RED
confirmed by running `mvn test-compile`: the test module fails to compile on the unimplemented
`LibraryItemType`, `WatchStatusRollup`, `LibraryService`, `LibraryController`, `LibraryListRequest`,
`LibraryItemResponse`, `LibraryListResponse` symbols and the two new repository finder methods,
consistent with the story-002/005/006/007/008 convention. No Flyway-migration-pin amendment needed
this time (unlike story-007/008): this story adds no new table, so
`TmdbPackageReadOnlyConventionTest`'s `V1..V6` pin is untouched.

Spec ambiguity surfaced to Brian, not resolved silently: AC-2 requires the series status shown in
this list to be "the rolled-up status per STORY-012," and AC-4 requires filtering by that same
status, even though STORY-012 is not a listed prerequisite of STORY-009 and comes after it in the
epic's own story order. This contract implements only the roll-up *computation* described by
STORY-012's own AC-2/AC-3 text (`WatchStatusRollup`, a pure stateless derivation) as a read-only
display and filter value - no settable status, no bulk-update action, and no STORY-012 acceptance
criterion is claimed here. When STORY-012 is designed, its contract should reuse `WatchStatusRollup`
unchanged rather than re-deriving the same rule. Full detail in the API contracts doc under "Spec
clarification already surfaced to Brian."

This is also the story that resolves the "and see" half of STORY-007 AC-6 and STORY-008 AC-7 left
open on those stories' PRs: `GET /api/library` is the user-facing read surface those contracts said
STORY-009 would own. No further action needed beyond what already exists - the isolation guarantee
those stories already built (every write bound to `principal.userId()`) is exactly what this
endpoint's user-scoping relies on.

Design iteration with Dev has not started yet - this is the Phase 1 commit. Per the workflow, at
least one Dev review round is required before implementation begins.
