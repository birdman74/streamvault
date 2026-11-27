# API Contracts - story-010: Set Movie Watch Status

## Overview

Adds one write path over the `library_movies` table created by STORY-007:
`PATCH /api/library/movies/{id}/status`. No new table, no Flyway migration - `status` is already
a `VARCHAR` column (`V5__create_library_movies_table.sql`); this story only adds application logic
to change it after insert.

The route is not added to `SecurityConfig`'s `permitAll()` set, so it falls under the existing
`anyRequest().authenticated()` rule exactly like the other `/api/library/**` routes - no
`SecurityConfig` change.

## Endpoint: PATCH /api/library/movies/{id}/status

Changes the status of one movie in the authenticated user's own library. Covers AC-1, AC-2, AC-3,
AC-4, AC-5, AC-6.

`{id}` is `library_movies.id` - the same id returned as `LibraryMovieResponse.id` from
`POST /api/library/movies` (STORY-007) and as `LibraryItemResponse.id` for `type == "MOVIE"` from
`GET /api/library` (STORY-009).

### Request

```json
{
  "status": "WATCHED"
}
```

| Field | Type | Rules |
|---|---|---|
| status | string | required, not blank (`UpdateMovieStatusRequest` bean validation). Must additionally be one of `PLANNED`, `CURRENTLY_WATCHING`, `WATCHED`, checked at the service layer (not bean validation) so the error message can name the invalid value and list the valid options - same split as `AddMovieRequest.status` and `UpdateAccountSettingsRequest.ratingType`. |

### Response - 200 OK

Same shape as `LibraryMovieResponse` (STORY-007), reflecting the now-current status:

```json
{
  "id": 1,
  "tmdbId": 27205,
  "title": "Inception",
  "releaseYear": 2010,
  "posterUrl": "https://image.tmdb.org/t/p/w500/inception.jpg",
  "status": "WATCHED",
  "addedAt": "2026-09-10T12:00:00Z"
}
```

### Response - 400 Bad Request (blank status)

Reuses STORY-001's existing `MethodArgumentNotValidException` handler format.

```json
{
  "error": "Validation failed",
  "fields": {
    "status": "Status is required"
  }
}
```

### Response - 400 Bad Request (unsupported status value)

Reuses STORY-007's `InvalidWatchStatusException` / handler unchanged - same message format as the
add path:

```json
{
  "error": "Invalid status 'SOON'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED."
}
```

No row is read or written on this path (AC-4's "leaves the existing status unchanged").

### Response - 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point
(`{"error":"Authentication required"}`), unchanged by this story.

### Response - 404 Not Found

Either the id does not exist at all, or it belongs to a different user. Both cases return the
identical body, so a caller cannot distinguish "no such row" from "someone else's row" (AC-5,
AC-6):

```json
{
  "error": "This movie is not in your library."
}
```

Nothing is written in this path.

## New Components (contract for Dev to implement against)

These do not exist yet, or are extended from their STORY-007 shape. Tests in this branch are
written against these names and will fail to compile until Dev implements them - the expected RED
state, consistent with STORY-007/009's contracts.

### `com.streamvault.backend.library.LibraryMovie` (extended)

New mutator, added alongside the existing getters-only set:

```java
public void setStatus(WatchStatus status) {
    this.status = status;
}
```

This is the *only* new mutator. Every other field stays set-once-at-construction exactly as
story-007 left it. (See `story-010-test-plan.md` Design Decision 4 for why story-007's
"no setters at all" test is being revised rather than worked around.)

### `com.streamvault.backend.library.LibraryMovieRepository` (extended)

New finder, additive, no change to any existing method:

```java
Optional<LibraryMovie> findByIdAndUserId(Long id, Long userId);
```

Returns `Optional.empty()` for both "no row with that id" and "row exists but belongs to a
different user" - never throws. Consistent with the existing cross-story repository invariant
("new finder methods handle empty results without throwing").

### `com.streamvault.backend.library.dto.UpdateMovieStatusRequest` (new)

```java
record UpdateMovieStatusRequest(
    @NotBlank(message = "Status is required") String status
)
```

### `com.streamvault.backend.library.exception.LibraryMovieNotFoundException` (new)

```java
class LibraryMovieNotFoundException extends RuntimeException {
    LibraryMovieNotFoundException() {
        super("This movie is not in your library.");
    }
}
```

Thrown by `LibraryMovieService.updateStatus` when `findByIdAndUserId` returns empty. Caught by
`GlobalExceptionHandler`, mapped to 404 with the message format shown above.

### `com.streamvault.backend.library.LibraryMovieService` (extended)

New method, added alongside the existing `addMovie`:

```java
public LibraryMovieResponse updateStatus(Long userId, Long libraryMovieId, String statusValue) {
    WatchStatus status = parseWatchStatus(statusValue);

    LibraryMovie movie = libraryMovieRepository.findByIdAndUserId(libraryMovieId, userId)
            .orElseThrow(LibraryMovieNotFoundException::new);

    movie.setStatus(status);
    LibraryMovie saved = libraryMovieRepository.save(movie);

    return toResponse(saved);
}
```

Order of operations, matching `addMovie`'s existing "validate before touching collaborators"
style and `AccountSettingsService.updateRatingType`'s precedent:

1. Resolve the status via the existing private `parseWatchStatus` helper - throws
   `InvalidWatchStatusException` on an unrecognized or wrong-case value, with **no**
   `LibraryMovieRepository` access on this path (AC-4).
2. `findByIdAndUserId(libraryMovieId, userId)` - empty means either "no such row" or "someone
   else's row"; both throw `LibraryMovieNotFoundException` (AC-5, AC-6).
3. Mutate and save. `toResponse` is the existing private mapper, reused unchanged.

Reuses `toResponse` and `parseWatchStatus` already present in `LibraryMovieService` from
STORY-007 - no duplicate mapping or parsing logic.

### `com.streamvault.backend.library.LibraryMovieController` (extended)

New endpoint, added alongside the existing `add`:

```java
@PatchMapping("/{id}/status")
public ResponseEntity<LibraryMovieResponse> updateStatus(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @PathVariable Long id,
        @Valid @RequestBody UpdateMovieStatusRequest request) {
    return ResponseEntity.ok(
            libraryMovieService.updateStatus(principal.userId(), id, request.status()));
}
```

The owning user id is always `principal.userId()` resolved from the JWT, matching every other
mutating endpoint in this epic - `UpdateMovieStatusRequest` has no `userId` component, so there is
nothing in the request for a client to override.

### `com.streamvault.backend.common.GlobalExceptionHandler` (extended)

New `@ExceptionHandler(LibraryMovieNotFoundException.class)` mapped to 404:

```java
@ExceptionHandler(LibraryMovieNotFoundException.class)
public ResponseEntity<Map<String, String>> handleLibraryMovieNotFound(LibraryMovieNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
}
```

### Database migration

None. No schema change.

## Out of Scope for this contract

- Series, season, or episode status endpoints (STORY-012).
- Watch date behavior on status change (STORY-013).
- Bulk status changes across multiple library items in one request.
- Any change to `POST /api/library/movies` or `GET /api/library`'s request/response shapes.
