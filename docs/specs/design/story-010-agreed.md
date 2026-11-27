# Agreed Design - story-010

## Summary
Test and Dev agree on the design after 1 round of review. Implementation can begin.

## Final API Contracts
`story-010-api-contracts.md` as committed in round 1, no revisions needed. One new write path over
`library_movies` (STORY-007): `PATCH /api/library/movies/{id}/status`, keyed by `library_movies.id`.
No new table, no Flyway migration. The route is not added to `SecurityConfig`'s `permitAll()` set.

## Implementation Plan
1. New `UpdateMovieStatusRequest` record (`library.dto`) with `@NotBlank(message = "Status is
   required") String status`.
2. New `LibraryMovieNotFoundException` (`library.exception`), no-arg constructor carrying the fixed
   message `"This movie is not in your library."`.
3. Extend `LibraryMovieRepository` with `Optional<LibraryMovie> findByIdAndUserId(Long id, Long
   userId)` - additive, no change to any existing finder.
4. Extend `LibraryMovie` with exactly one new mutator, `setStatus(WatchStatus status)`. Every other
   field stays set-once-at-construction.
5. Extend `LibraryMovieService` with `updateStatus(Long userId, Long libraryMovieId, String
   statusValue)`: resolve status via the existing private `parseWatchStatus` (zero repository access
   on an invalid value), `findByIdAndUserId(libraryMovieId, userId).orElseThrow(LibraryMovieNotFoundException::new)`,
   `movie.setStatus(status)`, `save`, map via the existing private `toResponse`. No new fields, no
   duplicate parsing/mapping logic.
6. Extend `LibraryMovieController` with `@PatchMapping("/{id}/status")` `updateStatus`, reading the
   owning user id only from `principal.userId()`.
7. Extend `GlobalExceptionHandler` with `@ExceptionHandler(LibraryMovieNotFoundException.class)` ->
   404, `Map.of("error", ex.getMessage())`.
8. No `SecurityConfig` change, no Flyway migration.

## Test Coverage Confirmation
All AC-1 through AC-6 are covered by Test's failing tests per `story-010-test-plan.md`'s coverage
table (verified against the actual committed test files, not just the table - see Test Code
Conformance below). No additional Dev unit tests are needed beyond what Test has already committed:
Test's own suite already exercises every unit-level concern this story introduces - the entity
mutator (`LibraryMovieTest`), the service algorithm including validation order and zero-repository-
access on an invalid value (`LibraryMovieServiceTest`), the repository finder's empty-without-
throwing behavior for both "no row" and "another user's row" (`LibraryMovieRepositoryTest`), the
bean-validation boundary (`UpdateMovieStatusRequestValidationTest`), and the exception's fixed
message (`LibraryMovieNotFoundExceptionTest`). This mirrors story-009, where Dev's review found
Test's own coverage already complete at the unit level.

Confirmed the expected Phase 1 RED state independently: `mvn test-compile` fails to compile only on
`LibraryMovieServiceTest`, `LibraryMovieTest`, `LibraryMovieRepositoryTest`,
`LibraryMovieControllerTest`, `LibraryMovieControllerPrincipalConventionTest`,
`UpdateMovieStatusRequestValidationTest`, and `LibraryMovieNotFoundExceptionTest` - each only on the
unimplemented symbols the contract lists (`updateStatus`, `findByIdAndUserId`, `setStatus`,
`UpdateMovieStatusRequest`, `LibraryMovieNotFoundException`). `LibraryEndpointsSecurityTest` compiles
clean as Test's log claims (it only calls `MockMvc`/`ObjectMapper`, never the new symbols directly).
No other file in the module is affected.

## Test Code Conformance
Checked against the final `story-010-api-contracts.md`, and cross-checked every constructor,
method signature, endpoint, DTO field, status code, and error message against the current committed
production code (`LibraryMovie`, `LibraryMovieRepository`, `LibraryMovieService`,
`LibraryMovieController`, `LibraryMovieResponse`, `WatchStatus`, `InvalidWatchStatusException`,
`GlobalExceptionHandler`, `AuthenticatedUser`, `LibraryListResponse`, `LibraryItemResponse`):

| Test file | Result |
|---|---|
| `LibraryEndpointsSecurityTest.java` | Conforms |
| `LibraryMovieControllerPrincipalConventionTest.java` | Conforms |
| `LibraryMovieControllerTest.java` | Conforms |
| `LibraryMovieRepositoryTest.java` | Conforms |
| `LibraryMovieServiceTest.java` | Conforms |
| `LibraryMovieTest.java` | Conforms |
| `UpdateMovieStatusRequestValidationTest.java` | Conforms |
| `LibraryMovieNotFoundExceptionTest.java` | Conforms |

All test code matches the final contracts: the `PATCH /api/library/movies/{id}/status` path, the
`updateStatus(Long userId, Long libraryMovieId, String statusValue)` parameter order, the
`findByIdAndUserId(Long id, Long userId)` parameter order, the `setStatus(WatchStatus)` mutator, the
`UpdateMovieStatusRequest` field and its blank-message text, the `LibraryMovieNotFoundException`
message text, and the 200/400/404 status codes are each used identically across every test file and
match the contract.

### Minor note (non-blocking)
`story-010-test-plan.md`'s AC coverage table names a class `LibraryMovieEndpointsSecurityTest` in six
places; the file Test actually committed is `LibraryEndpointsSecurityTest` (the same class
story-007 already owns, amended rather than created new - as the story-010 log entry correctly
states). This is a naming slip in the test plan's traceability table only; the committed test code
itself matches the contracts in every respect, so it does not block agreement. Flagging so the table
can be corrected if Test revises this document for any other reason.
