# API Contracts — story-005: Account Settings for Rating Type Preference

## Overview

Adds a new account settings resource scoped to the authenticated user (resolved from the JWT via
the existing `AuthenticatedUser` principal, same as `AuthController.me()`). This story only
exposes the rating type preference; the resource is intentionally named generically
(`/api/account/settings`, not `/api/account/settings/rating-type`) because the deferred Account
Settings epic (`docs/specs/backlog.md`) is expected to add more fields to the same resource later.
No other field is defined by this contract.

Both endpoints require authentication. Neither is added to `SecurityConfig`'s `permitAll()`
matcher list, so both fall under the existing `anyRequest().authenticated()` rule with no
`SecurityConfig` changes required.

## Rating Type Values

Exactly three values exist, satisfying AC-2. The wire value is the Java enum constant name,
sent and received as a plain JSON string (not nested), matched with exact case:

| Wire value (request/response string) | Enum constant | Story's human-readable label |
|---|---|---|
| `LOVE_LIKE_MEH_DISLIKE_HATE` | `RatingType.LOVE_LIKE_MEH_DISLIKE_HATE` | Love / Like / Meh / Dislike / Hate |
| `THUMBS_UP_THUMBS_DOWN` | `RatingType.THUMBS_UP_THUMBS_DOWN` | Thumbs Up / Thumbs Down |
| `HALF_STAR_OUT_OF_5` | `RatingType.HALF_STAR_OUT_OF_5` | Half-star out of 5 |

Matching is case-sensitive and exact. `love_like_meh_dislike_hate` or any other casing/spacing
variant is rejected the same as a nonsense string (AC-6) — the contract does not require
normalization, and tests pin this down so a future round doesn't silently add case-insensitive
matching as an undocumented behavior change.

## Endpoint: GET /api/account/settings

Returns the authenticated user's current settings. Covers AC-1 and AC-4 (a read always reflects
the last successfully persisted write).

### Response — 200 OK

```json
{
  "ratingType": "LOVE_LIKE_MEH_DISLIKE_HATE"
}
```

### Response — 401 Unauthorized

No JWT / invalid JWT. Reuses the existing `SecurityConfig` authentication entry point
(`{"error":"Authentication required"}`), unchanged by this story.

## Endpoint: PATCH /api/account/settings

Updates the authenticated user's rating type preference. Covers AC-2, AC-5, AC-6.

### Request

```json
{
  "ratingType": "THUMBS_UP_THUMBS_DOWN"
}
```

| Field | Type | Rules |
|---|---|---|
| ratingType | string | required, not blank (`UpdateAccountSettingsRequest` bean validation). Must additionally be one of the three wire values above, checked at the service layer (not bean validation) so the error message can name the invalid value and list the valid options. |

Only the fields in the request body are affected. Today there is only one field, so the whole
resource is effectively replaced, but the endpoint is not named or shaped to preclude adding more
optional fields to this same request/response pair in a future story.

### Response — 200 OK

Same shape as the GET response, reflecting the now-current value:

```json
{
  "ratingType": "THUMBS_UP_THUMBS_DOWN"
}
```

### Response — 400 Bad Request (blank ratingType)

Reuses STORY-001's existing `MethodArgumentNotValidException` handler format.

```json
{
  "error": "Validation failed",
  "fields": {
    "ratingType": "Rating type is required"
  }
}
```

### Response — 400 Bad Request (unsupported ratingType value)

```json
{
  "error": "Invalid rating type 'FIVE_STARS'. Valid options are: LOVE_LIKE_MEH_DISLIKE_HATE, THUMBS_UP_THUMBS_DOWN, HALF_STAR_OUT_OF_5."
}
```

The invalid value is echoed back exactly as submitted (quoted) so the user/frontend can show what
was rejected. No account row is modified in this path (AC-6's "leaves the existing preference
unchanged").

### Response — 401 Unauthorized

Same as GET.

## New Components (contract for Dev to implement against)

These do not exist yet. Tests in this branch are written against these names and will fail to
compile until Dev implements them — that failure to compile/run is the expected RED state for this
story, consistent with STORY-002's contract.

### `com.streamvault.backend.user.RatingType`
```java
enum RatingType {
    LOVE_LIKE_MEH_DISLIKE_HATE,
    THUMBS_UP_THUMBS_DOWN,
    HALF_STAR_OUT_OF_5
}
```

### `com.streamvault.backend.user.User` (extended)
- New field: `@Enumerated(EnumType.STRING) @Column(name = "rating_type", nullable = false) private RatingType ratingType = RatingType.LOVE_LIKE_MEH_DISLIKE_HATE;`
  The field initializer (rather than constructor logic) is what satisfies AC-3 for both the local
  `User(String, String)` constructor and the `googleUser(...)` factory without modifying either
  constructor body, so STORY-001/STORY-002 construction paths and their existing tests are
  untouched.
- New accessor: `RatingType getRatingType()`
- New mutator: `void setRatingType(RatingType ratingType)`

### `com.streamvault.backend.settings.dto.AccountSettingsResponse`
```java
record AccountSettingsResponse(String ratingType)
```
`ratingType` is `RatingType.name()`, not the enum type itself, so the JSON wire value is always
the plain string shown in the table above regardless of how Jackson is configured elsewhere.

### `com.streamvault.backend.settings.dto.UpdateAccountSettingsRequest`
```java
record UpdateAccountSettingsRequest(
    @NotBlank(message = "Rating type is required") String ratingType
)
```

### `com.streamvault.backend.settings.exception.InvalidRatingTypeException`
```java
class InvalidRatingTypeException extends RuntimeException {
    InvalidRatingTypeException(String invalidValue);
    String getInvalidValue();
}
```
Thrown by `AccountSettingsService.updateRatingType` when `request.ratingType()` does not exactly
match one of the three `RatingType` constant names. The value must be parsed/validated before any
`UserRepository` access at all (neither `findById` nor `save`) — the row is not read or written on
this path — so an invalid value throws even for a `userId` that would otherwise fail to resolve.
Caught by `GlobalExceptionHandler`, mapped to 400 with the message format shown above.

### `com.streamvault.backend.settings.AccountSettingsService`
```java
class AccountSettingsService {
    AccountSettingsService(UserRepository userRepository);
    AccountSettingsResponse getSettings(Long userId);
    AccountSettingsResponse updateRatingType(Long userId, UpdateAccountSettingsRequest request);
}
```
Both methods resolve the row via `userRepository.findById(userId)` using only the id taken from
the authenticated principal — never any id supplied by the request body or path — which is what
makes AC-5 hold (one user's request can never read or write another user's row). `userId` not
existing is not a reachable state given a validly-issued JWT and is not a case this story tests
(same reasoning as the rest of the codebase not testing "authenticated but the row was deleted
out from under the session").

### `com.streamvault.backend.settings.AccountSettingsController`
```java
@RestController
@RequestMapping("/api/account/settings")
class AccountSettingsController {
    AccountSettingsController(AccountSettingsService accountSettingsService);

    @GetMapping
    ResponseEntity<AccountSettingsResponse> getSettings(@AuthenticationPrincipal AuthenticatedUser principal);

    @PatchMapping
    ResponseEntity<AccountSettingsResponse> updateSettings(
        @AuthenticationPrincipal AuthenticatedUser principal,
        @Valid @RequestBody UpdateAccountSettingsRequest request);
}
```

### `com.streamvault.backend.common.GlobalExceptionHandler` (extended)
New `@ExceptionHandler(InvalidRatingTypeException.class)` mapped to 400, message format above.

### Database migration (new)
`V4__add_rating_type_to_users.sql`:
```sql
ALTER TABLE users ADD COLUMN rating_type VARCHAR(50) NOT NULL DEFAULT 'LOVE_LIKE_MEH_DISLIKE_HATE';
```
Existing rows (none expected in any real environment yet, since no library data ships before this
epic) receive the default via the column default, consistent with the entity-level default for new
rows going forward.

## Out of Scope for this contract
- Any settings field other than rating type (password change, delete account, profile/email
  updates) — deferred Account Settings epic.
- Converting or re-scaling ratings recorded under a previous rating type (STORY-015 concern, not
  this story's).
- `SecurityConfig` changes — this story adds no new `permitAll()` entries.
