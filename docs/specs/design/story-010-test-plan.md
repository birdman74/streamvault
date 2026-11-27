# Test Plan - story-010: Set Movie Watch Status

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | A signed-in user can set the status of a movie in their own library to Planned, Currently Watching, or Watched | `LibraryMovieServiceTest.should_updateStoredStatus_when_statusIsSetTo[PLANNED\|CURRENTLY_WATCHING\|WATCHED]`, `LibraryMovieControllerTest.should_return200WithTheUpdatedEntry_when_statusIsChanged`, `LibraryMovieEndpointsSecurityTest.should_updateStatusEndToEnd_when_aValidStatusIsSubmitted` |
| AC-2 | The new status persists and is returned on subsequent reads of that library item and in the library list from STORY-009 | `LibraryMovieServiceTest.should_persistStatusChange_when_readBackAfterUpdate`, `LibraryMovieRepositoryTest.should_roundTripAnUpdatedStatus_when_savedAfterSetStatus`, `LibraryMovieEndpointsSecurityTest.should_reflectTheNewStatus_when_libraryListIsFetchedAfterAnUpdate` |
| AC-3 | Status can be changed any number of times and in any direction, including Watched back to Planned | `LibraryMovieServiceTest.should_allowAnyNumberOfTransitionsInAnyDirection_when_statusIsChangedRepeatedly`, `LibraryMovieEndpointsSecurityTest.should_allowWatchedBackToPlanned_when_statusIsChangedTwiceEndToEnd` |
| AC-4 | A status value outside the three allowed values is rejected with a clear, user-facing error and leaves the existing status unchanged | `LibraryMovieServiceTest.should_throwInvalidWatchStatusException_when_updateStatusValueIsNotARecognizedValue`, `should_throwInvalidWatchStatusException_when_updateStatusValueDiffersOnlyInCase`, `should_leaveStoredStatusUnchanged_when_updateStatusValueIsInvalid`, `LibraryMovieControllerTest.should_return400WithClearMessage_when_updateStatusIsUnsupported`, `UpdateMovieStatusRequestValidationTest.should_rejectRequest_when_statusIs[Blank\|Null]`, `LibraryMovieEndpointsSecurityTest.should_leaveStatusUnchanged_when_anInvalidStatusValueIsSubmittedEndToEnd` |
| AC-5 | A user can only change the status of movies in their own library; a request against another user's item is rejected and changes nothing | `LibraryMovieRepositoryTest.should_notFindAnotherUsersMovie_when_updatingByIdAndUserId`, `LibraryMovieServiceTest.should_throwLibraryMovieNotFoundException_when_theItemBelongsToAnotherUser`, `LibraryMovieServiceTest.should_notSaveAnything_when_theItemBelongsToAnotherUser`, `LibraryMovieEndpointsSecurityTest.should_return404AndChangeNothing_when_aUserTriesToUpdateAnotherUsersMovie` |
| AC-6 | Setting status on a movie that is not in the user's library returns a clear, user-facing error | `LibraryMovieServiceTest.should_throwLibraryMovieNotFoundException_when_noMovieMatchesTheIdForThatUser`, `LibraryMovieControllerTest.should_return404WithClearMessage_when_updateStatusTargetsAMovieNotInTheLibrary`, `LibraryMovieNotFoundExceptionTest.should_carryTheFixedUserFacingMessage_when_constructed`, `LibraryMovieEndpointsSecurityTest.should_return404_when_updatingStatusOfANonexistentLibraryMovie` |

## Cross-Story Invariants

| Invariant | Test(s) |
|---|---|
| Repository finder methods return "no match" without throwing (never an exception for an absent row) | `LibraryMovieRepositoryTest.should_returnEmptyOptional_when_findByIdAndUserIdMatchesNoRow`, `should_returnEmptyOptional_when_findByIdAndUserIdMatchesAnIdButADifferentUser` |
| `LibraryMovie` is immutable by construction except where a story explicitly introduces a mutator | `LibraryMovieTest.should_exposeGettersAndOnlyTheStatusMutator_when_methodsAreInspected` (revises story-007's `should_exposeGettersOnlyAndNoSetters`, see Design Decisions below) |
| New/changed endpoints stay under `SecurityConfig`'s `anyRequest().authenticated()` rule; no new `permitAll()` entries | `LibraryMovieEndpointsSecurityTest.should_return401_when_updateStatusIsCalledWithoutAuthentication` |
| The owning user id is always `principal.userId()` from the JWT, never from the request body or path trusted beyond the authorization check | `LibraryMovieControllerPrincipalConventionTest.should_threadResolvedPrincipalUserIdIntoUpdateStatus_when_statusIsChanged`, `should_useADifferentPrincipalUserId_when_aDifferentUserChangesStatus` |

## API Contracts

See `story-010-api-contracts.md`.

## Design Decisions Made Explicit

Test's own implementation calls, not spec ambiguities, recorded here for Dev and Brian's
visibility (same discipline as story-007/008/009):

1. **Endpoint is `PATCH /api/library/movies/{id}/status`, keyed by `library_movies.id` (the same
   id returned in `LibraryMovieResponse` and in `LibraryItemResponse.id` for `type == "MOVIE"`
   per story-009's own `id` note).** Not by `tmdbId`, since `tmdbId` is not unique per user across
   repeated TMDB catalog state and `id` is already the identifier this epic uses to key a single
   library row (story-009 design decision 7).
2. **A request against another user's item and a request against a nonexistent item both return
   404 with the same message.** Matches this codebase's existing cross-user-access pattern:
   `LibraryMovieRepository` has no unscoped finder, and every existing finder already returns "no
   match" for another user's row rather than exposing that the row exists for someone else. AC-5
   only requires the request be "rejected and changes nothing" - it does not mandate a status code
   - and collapsing both cases into one 404 avoids leaking whether a given id belongs to another
   user. There is no precedent for 403 anywhere in this codebase; introducing one here would be a
   new, undocumented pattern.
3. **Status is validated before the library row is looked up, mirroring
   `AccountSettingsService.updateRatingType`'s order.** An unsupported status value throws
   `InvalidWatchStatusException` with zero `LibraryMovieRepository` access, even for an id that
   does not belong to the caller. This keeps the "no repository access on an invalid value" rule
   from story-007's `addMovie` consistent between the add and update paths.
4. **`LibraryMovie` gains exactly one mutator, `setStatus(WatchStatus)`, not a generic setter per
   field.** Story-007's `LibraryMovieTest` asserted the entity exposed no `set*` method at all
   ("Getters only, no setters: this story sets every field once at construction"). That invariant
   predates this story's requirement (AC-1/AC-3) to change `status` after construction, and the
   two cannot both hold. Test is revising `LibraryMovieTest.should_exposeGettersOnlyAndNoSetters`
   in this round to `should_exposeGettersAndOnlyTheStatusMutator_when_methodsAreInspected`, which
   now asserts `setStatus` is the *only* `set*`-prefixed method, preserving the spirit of the
   original invariant (no uncontrolled field mutation) while allowing the one mutation this story
   requires. No other entity field gains a setter.
5. **The service method signature is `LibraryMovieResponse updateStatus(Long userId, Long
   libraryMovieId, String statusValue)`**, mirroring `addMovie`'s existing parameter shape (plain
   `String` status, parsed internally) and `AccountSettingsService.updateRatingType`'s `(Long
   userId, ...)` convention for a user-scoped write.
6. **No new Flyway migration.** `status` is already a `VARCHAR` column on `library_movies`
   (`V5__create_library_movies_table.sql`); this story only adds application logic to change its
   value after insert.

## Test Strategy

Mirrors story-007/009's layering:
- **Unit (Mockito)**: `LibraryMovieServiceTest` (service algorithm: validation order, not-found,
  cross-user, repeated transitions, unchanged-on-invalid) and `LibraryMovieTest` (entity mutator).
- **Bean validation**: `UpdateMovieStatusRequestValidationTest` (blank/null `status` only; the
  enumerated-value check is a service concern, consistent with `AddMovieRequest` / `UpdateAccountSettingsRequest`).
- **Controller slice (`@WebMvcTest`, ADR-001 Layer 1)**: `LibraryMovieControllerTest` (response
  shape and status-code mapping for 200/400/404) and `LibraryMovieControllerPrincipalConventionTest`
  (principal id threading).
- **Full-stack (`@SpringBootTest` + H2, ADR-001 Layer 2)**: new methods added to
  `LibraryMovieEndpointsSecurityTest` (401 boundary, persistence visible via `GET /api/library`,
  repeated transitions, cross-user isolation, nonexistent id).
- **Repository (`@SpringBootTest` + H2)**: new methods added to `LibraryMovieRepositoryTest` for
  `findByIdAndUserId`.
- **Exception unit test**: `LibraryMovieNotFoundExceptionTest`, mirroring
  `DuplicateLibraryMovieExceptionTest`.

## Out of Scope

- Series, season, or episode status (STORY-012) - no changes to `LibrarySeries` / `LibrarySeason`
  / `LibraryEpisode` or their services.
- Watch date behavior on status change (STORY-013) - no `watchedAt` field or clearing logic.
- Bulk status changes across multiple library items - the endpoint accepts exactly one `id`.
- Rating or note side effects (STORY-014, STORY-015).
- Any change to `POST /api/library/movies` (story-007) or `GET /api/library` (story-009) request
  shapes - this story is additive only.
