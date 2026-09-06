# Agreed Design — story-005

## Summary
Test and Dev agree on the design after 1 round of review. Dev reviewed
`story-005-test-plan.md` and `story-005-api-contracts.md` against the PO story's
acceptance criteria, the existing codebase (`User`, `UserRepository`, `AuthenticatedUser`,
`AuthController`, `GlobalExceptionHandler`, `SecurityConfig`, migrations V1-V3), and the
already-committed failing test files (`RatingTypeTest`, `UserTest` additions,
`AccountSettingsServiceTest`, `AccountSettingsControllerTest`,
`UpdateAccountSettingsRequestValidationTest`). No concerns found. Proceeding to
implementation.

## Final API Contracts
As defined in `story-005-api-contracts.md`, no amendments:
- `GET /api/account/settings` and `PATCH /api/account/settings`, both authenticated only
  via the existing JWT filter chain, no `SecurityConfig` changes.
- Wire value for `ratingType` is the exact enum constant name, case-sensitive, on both
  request and response.
- `@NotBlank` bean validation on `UpdateAccountSettingsRequest.ratingType`; enum-membership
  validation is a service-layer concern (`InvalidRatingTypeException`) so the 400 message can
  name the invalid value and list the three valid options.
- `AccountSettingsService` resolves the row exclusively via `userId` taken from
  `AuthenticatedUser`, never from the request body, satisfying AC-5.
- Invalid rating type values are rejected before any `UserRepository` access, satisfying
  AC-6's "leaves the existing preference unchanged" without needing a rollback.
- `User.ratingType` defaults via field initializer, not constructor logic, so both the
  local `User(String, String)` constructor and the `googleUser(...)` factory pick up
  AC-3's default without either constructor body changing.
- `V4__add_rating_type_to_users.sql` adds `rating_type VARCHAR(50) NOT NULL DEFAULT
  'LOVE_LIKE_MEH_DISLIKE_HATE'`, independent of the existing
  `chk_users_password_hash_or_google_id` check constraint's columns.

## Implementation Plan
1. Migration `V4__add_rating_type_to_users.sql` per the contract.
2. Add `com.streamvault.backend.user.RatingType` enum with the three constants.
3. Extend `User`: add `ratingType` field with `@Enumerated(EnumType.STRING)` and the field
   initializer default, plus getter/setter. Existing constructors untouched.
4. Add `com.streamvault.backend.settings` package:
   - `dto.AccountSettingsResponse` (record)
   - `dto.UpdateAccountSettingsRequest` (record, `@NotBlank`)
   - `exception.InvalidRatingTypeException` (invalid value + `getInvalidValue()`)
   - `AccountSettingsService` (constructor-injected `UserRepository`; validates the
     requested value against `RatingType` names before touching the repository)
   - `AccountSettingsController` (`@RestController`, both endpoints, `@AuthenticationPrincipal`)
5. Extend `GlobalExceptionHandler` with an `InvalidRatingTypeException` handler mapped to
   400, message format per the contract.
6. No `SecurityConfig` changes.

## Test Coverage Confirmation
All AC-1 through AC-6 are covered by Test's already-committed failing tests, as mapped in
`story-005-test-plan.md`. Dev will add unit tests for:
- `RatingType.valueOf(...)`/name-matching edge behavior at whatever internal seam the
  service implementation introduces to distinguish a valid enum name from an unsupported
  one, if that logic ends up more than a single `try/catch` around `RatingType.valueOf`.
- `InvalidRatingTypeException`'s message construction (the exact "Invalid rating type
  'X'. Valid options are: ..." string), since `GlobalExceptionHandler`'s existing tests
  cover its other handlers by asserting response bodies, and this handler should follow
  the same pattern.
