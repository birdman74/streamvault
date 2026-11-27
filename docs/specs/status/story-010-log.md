# STORY-010 Log: Set Movie Watch Status

> Full phase-by-phase trace of Dev/Test work on this story. STATUS.md carries only a
> one-line current status for this story; this file is the detailed record used during
> PR review. Newest entries at the top.

Spec: `docs/specs/story-010-set-movie-watch-status.md`
Branch: `feature/story-010-set-movie-watch-status`

---

## Phase 1: Dev design review, round 1 (2026-11-27)

Agreed after round 1 - no feedback round needed. Read the story, the test plan, the API contracts,
and ran the Test Code Conformance check against all eight test files Test committed
(`LibraryEndpointsSecurityTest`, `LibraryMovieControllerPrincipalConventionTest`,
`LibraryMovieControllerTest`, `LibraryMovieRepositoryTest`, `LibraryMovieServiceTest`,
`LibraryMovieTest`, `UpdateMovieStatusRequestValidationTest`, `LibraryMovieNotFoundExceptionTest`),
cross-checked against the current committed production code (`LibraryMovie`,
`LibraryMovieRepository`, `LibraryMovieService`, `LibraryMovieController`, `LibraryMovieResponse`,
`WatchStatus`, `InvalidWatchStatusException`, `GlobalExceptionHandler`) rather than the contract
prose alone. Every constructor, method signature, endpoint, DTO field, status code, and error
message used in the tests matches the final `story-010-api-contracts.md`. Independently reran
`mvn test-compile` and confirmed the expected RED state matches Test's claim exactly: only the seven
files that reference the five unimplemented symbols (`updateStatus`, `findByIdAndUserId`,
`setStatus`, `UpdateMovieStatusRequest`, `LibraryMovieNotFoundException`) fail to compile;
`LibraryEndpointsSecurityTest` compiles clean as claimed.

One non-blocking documentation note: `story-010-test-plan.md`'s AC coverage table names a class
`LibraryMovieEndpointsSecurityTest` that does not exist - the actual (and correctly named, per the
story-010 log's own Phase 1 entry) file is `LibraryEndpointsSecurityTest`, the same class story-007
already owns. This is a naming slip in the traceability table only; the committed test code itself
is correct and matches the contracts, so it does not block agreement.

No test/contract mismatches found. No acceptance-criteria ambiguity found. Agreed design committed
to `story-010-agreed.md`; proceeding to Phase 2 implementation.

---

## Phase 1: Test goes first (2026-11-27)

Complete on branch `feature/story-010-set-movie-watch-status`, cut from `main` after STORY-009
merged (PR #31). Test plan (`docs/specs/design/story-010-test-plan.md`) maps every AC-1..AC-6 plus
the cross-story invariants to named tests; API contracts (`docs/specs/design/story-010-api-contracts.md`)
define one new write path over `library_movies` (STORY-007): `PATCH /api/library/movies/{id}/status`.
No new table and no Flyway migration - `status` is already a `VARCHAR` column; this story only adds
application logic to change it after insert. No `SecurityConfig` change: the route already falls
under the existing `anyRequest().authenticated()` rule.

Six design decisions recorded as Test's implementation calls rather than spec ambiguities (see the
API contracts doc and the test plan's "Design Decisions Made Explicit"): (1) the endpoint is keyed
by `library_movies.id` (the same id STORY-007/009 already return), not `tmdbId`; (2) a request
against another user's item and a request against a nonexistent item both return the identical 404
body, matching this codebase's existing cross-user-access pattern of never exposing whether an id
belongs to someone else, and consistent with there being no 403 precedent anywhere in this API;
(3) status is validated before the library row is looked up, mirroring
`AccountSettingsService.updateRatingType`'s order, so an unsupported value touches the repository
zero times even for an id the caller does not own; (4) `LibraryMovie` gains exactly one mutator,
`setStatus(WatchStatus)` - see below for why this required revising a story-007 test; (5) the
service method is `updateStatus(Long userId, Long libraryMovieId, String statusValue)`, mirroring
`addMovie`'s existing parameter shape; (6) no new Flyway migration.

**Cross-story invariant conflict found and resolved**: story-007's `LibraryMovieTest` asserted the
entity exposed no `set*` method at all ("Getters only, no setters: this story sets every field once
at construction"). That invariant predates this story's AC-1/AC-3 requirement to change `status`
after construction, and the two cannot both hold - this is exactly the kind of cross-story
invariant check Phase 1 calls for when a new story touches shared infrastructure. Resolved by
revising `LibraryMovieTest.should_exposeGettersOnlyAndNoSetters` to
`should_exposeGettersAndOnlyTheStatusMutator_when_methodsAreInspected`, which now asserts
`setStatus` is the *only* `set*`-prefixed method on the class - preserving the original invariant's
intent (no uncontrolled field mutation) while allowing the one mutation this story requires. No
other entity in the codebase is affected.

Seven test files touched, two new:
- `dto/UpdateMovieStatusRequestValidationTest` (new, plain Jakarta Validator, pins `@NotBlank` on
  `status` only - mirrors `UpdateAccountSettingsRequestValidationTest`).
- `exception/LibraryMovieNotFoundExceptionTest` (new, mirrors `DuplicateLibraryMovieExceptionTest`,
  pins the fixed 404 message).
- `LibraryMovieServiceTest` (amended): `updateStatus` algorithm - all three status values store
  correctly (AC-1), persists and reads back (AC-2), any number of transitions in any direction
  including Watched back to Planned (AC-3), invalid value throws `InvalidWatchStatusException`
  with zero repository access and leaves the in-memory entity's status untouched (AC-4), not-found
  and cross-user both throw `LibraryMovieNotFoundException` with no save (AC-5, AC-6), and the
  lookup is always keyed by both the path id and the principal's user id together.
- `LibraryMovieControllerTest` (amended): 200 with updated body, 400 for blank status (bean
  validation) and for an unsupported value (service exception mapping), 404 for
  `LibraryMovieNotFoundException` mapping.
- `LibraryMovieControllerPrincipalConventionTest` (amended): `principal.userId()` threads into
  `updateStatus`, two different principals route to two different calls.
- `LibraryMovieRepositoryTest` (amended): new `findByIdAndUserId` finder - empty-without-throwing
  for both a nonexistent id and another user's id (cross-story repository invariant), finds the
  correct row when both id and user match, and a `setStatus` + `save` round-trips through a fresh
  `findByIdAndUserId`.
- `LibraryEndpointsSecurityTest` (amended, not a new file - same controller class story-007 already
  owns this file for): 401 boundary, full-stack update returning 200, the updated status visible in
  a subsequent `GET /api/library` (AC-2's "library list from STORY-009" clause), Watched-back-to-
  Planned end to end (AC-3), an invalid value leaving the list's status unchanged (AC-4), a second
  user's attempt against the first user's movie returning 404 and changing nothing (AC-5), and a
  nonexistent id returning 404 (AC-6).

Expected Phase 1 RED confirmed by running `mvn test-compile`: the test module fails to compile only
on the unimplemented `UpdateMovieStatusRequest`, `LibraryMovieNotFoundException`,
`LibraryMovieService.updateStatus`, `LibraryMovieRepository.findByIdAndUserId`, and
`LibraryMovie.setStatus` symbols - no other file in the module is affected, consistent with the
story-002/005/006/007/008/009 convention. `LibraryEndpointsSecurityTest` itself compiles clean
(it only calls `MockMvc`/`ObjectMapper`, never the new symbols directly), so its new methods will
fail at runtime with 404 "No endpoint" instead of a compile error until Dev adds the route - still
the correct RED state.

No PO question raised this round - all six acceptance criteria read unambiguously against the
existing STORY-007/009 contracts, and `WatchStatus`'s own javadoc already anticipated "STORY-010
sets it on movies" before this story began.

Design iteration with Dev has not started yet - this is the Phase 1 commit. Per the workflow, at
least one Dev review round is required before implementation begins.
