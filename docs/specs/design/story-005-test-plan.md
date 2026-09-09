# Test Plan — story-005: Account Settings for Rating Type Preference

## Acceptance Criteria Coverage

| AC | Criterion | Test(s) |
|---|---|---|
| AC-1 | A signed-in user can view their current rating type preference in an account settings area | `AccountSettingsControllerTest.should_return200WithCurrentRatingType_when_authenticatedUserRequestsSettings`; `AccountSettingsServiceTest.should_returnCurrentRatingType_when_settingsAreRead` |
| AC-2 | A user can change their rating type preference to any one of exactly three options | `AccountSettingsServiceTest.should_updateStoredRatingType_when_ratingTypeIsSetTo` (parameterized over all 3 wire values); `RatingTypeTest.should_defineExactlyThreeOptions_when_valuesAreListed` + `should_includeAllThreeContractuallyRequiredOptions_when_valuesAreListed` (pins the set to exactly these three, no more/fewer); `AccountSettingsControllerTest.should_return200WithUpdatedRatingType_when_validRatingTypeIsSubmitted` |
| AC-3 | A newly registered account defaults to "Love / Like / Meh / Dislike / Hate" without the user taking any action | `UserTest.should_defaultToLoveLikeMehDislikeHateRatingType_when_localAccountIsConstructed` (STORY-001 registration path); `UserTest.should_defaultToLoveLikeMehDislikeHateRatingType_when_googleUserIsConstructed` (STORY-002 registration path) |
| AC-4 | A changed rating type preference persists across sessions and is returned on every subsequent read | `AccountSettingsServiceTest.should_persistRatingTypeChange_when_readAfterUpdate` (update then read returns the updated value, not the original); `AccountSettingsServiceTest.should_updateStoredRatingType_when_ratingTypeIsSetTo` asserts `userRepository.save(...)` is actually called, not just that the in-memory response looks right |
| AC-5 | The rating type preference is per user; one user's change never affects another user's preference | `AccountSettingsServiceTest.should_onlyReadOrWriteTheAuthenticatedUsersRow_when_settingsAreAccessed` (two distinct mocked users/ids; asserts `findById`/`save` are only ever invoked with the id belonging to the request being serviced, never the other user's) |
| AC-6 | A request to set the rating type to any value outside the three supported options is rejected with a clear, user-facing error and leaves the existing preference unchanged | `AccountSettingsServiceTest.should_throwInvalidRatingTypeException_when_ratingTypeValueIsUnsupported`; `should_notCallSaveOnUserRepository_when_ratingTypeValueIsUnsupported`; `should_throwInvalidRatingTypeException_when_ratingTypeValueDiffersOnlyInCase` (locks case-sensitive matching as an explicit decision, not an accident); `AccountSettingsControllerTest.should_return400WithClearMessage_when_ratingTypeIsUnsupportedValue`; `AccountSettingsControllerTest.should_return400_when_ratingTypeIsBlank`; `UpdateAccountSettingsRequestValidationTest.should_rejectRequest_when_ratingTypeIsBlank` + `should_rejectRequest_when_ratingTypeIsNull` |

## Cross-Story Invariants

| Invariant | Test(s) |
|---|---|
| The `users` table's existing `chk_users_password_hash_or_google_id` constraint (STORY-002/V3) must survive a schema change that adds a new NOT NULL column to the same table | No new test added — `UserTableConstraintsTest` already exercises this constraint directly against the real Flyway-migrated schema and will re-run against the schema including `V4__add_rating_type_to_users.sql` as part of the full suite in Phase 3 verification. A new NOT NULL column with a DEFAULT does not require backfill and cannot violate that CHECK constraint, so no additional regression test is warranted here beyond the existing one continuing to pass. |
| Settings reads/writes must be scoped strictly by the JWT-derived `AuthenticatedUser.userId()`, never by any client-supplied id | `AccountSettingsServiceTest.should_onlyReadOrWriteTheAuthenticatedUsersRow_when_settingsAreAccessed` (see AC-5); `AccountSettingsController`'s contract takes no id from the path or body at all, which the controller test's request bodies structurally confirm (no `userId` field exists to smuggle a different id through) |
| Existing STORY-001/STORY-002 user-construction paths must remain unaffected by the new column | `UserTest`'s existing tests (`googleUserFactoryCreatesUserWithNullPasswordHashAndGivenGoogleIdAndEmail`, the two `IllegalArgumentException` tests) are untouched, not modified, and continue to pass; the two new default-rating-type tests are additive |

## API Contracts

See `story-005-api-contracts.md`.

## Test Strategy

- **Unit test (`RatingTypeTest`)**: plain JUnit, no Spring context. Pins the enum to exactly the
  three required constants and their exact names, since the wire format in the API contract is the
  literal enum constant name.
- **Unit test (`UserTest`, extended)**: plain JUnit, no Spring context, mirroring the existing
  style in this file. Adds two tests confirming the default rating type on construction via both
  existing factory paths.
- **Unit tests (`AccountSettingsServiceTest`)**: Mockito-based, mirroring `AuthServiceTest`'s style.
  Mocks `UserRepository` so no database is involved. This is where the bulk of AC coverage lives
  (AC-1, AC-2, AC-4, AC-5, AC-6).
- **DTO validation test (`UpdateAccountSettingsRequestValidationTest`)**: plain Jakarta Validator
  test, mirroring `RegisterRequestValidationTest`. Only checks the `@NotBlank` bean-validation
  concern; enum-membership validation is a service-layer concern per the contract and is tested in
  `AccountSettingsServiceTest`/`AccountSettingsControllerTest` instead.
- **Controller slice test (`AccountSettingsControllerTest`)**: `@WebMvcTest(AccountSettingsController.class)`
  with `@AutoConfigureMockMvc(addFilters = false)`, mirroring `AuthControllerGoogleTest`. Mocks
  `AccountSettingsService`. Uses Spring Security Test's `authentication(...)` request
  post-processor to populate `@AuthenticationPrincipal` with a fixed `AuthenticatedUser`, since no
  existing test in this codebase exercises `@AuthenticationPrincipal` in a `@WebMvcTest` slice yet
  — this establishes that pattern for the first time.
- No integration/Testcontainers-backed test touches the real `users` table or
  `V4__add_rating_type_to_users.sql` directly beyond what `UserTableConstraintsTest` already
  covers incidentally by running against the full migrated schema. Consistent with STORY-001 and
  STORY-002, which also have no dedicated DB-integration test for their migrations.
- Security-filter-chain behavior (that `/api/account/settings` is actually reachable only when
  authenticated through the real `SecurityConfig`, not just in a slice test with filters disabled
  and a manually-injected principal) is out of scope for automated coverage in this story, matching
  the existing accepted gap for `/api/auth/login`, `/api/auth/register`, and `/api/auth/google` in
  STORY-001/STORY-002.

## Out of Scope

- Frontend account settings UI (AC-1's "account settings area" UI half) — no frontend test project
  exists yet in this repo.
- Converting or re-scaling ratings recorded under a previous rating type — explicitly out of scope
  per the story notes; that is STORY-015's open question to resolve, not this story's.
- Behavior when the authenticated JWT's userId does not correspond to any existing row — not a
  reachable state given how JWTs are issued in this codebase (STORY-001/STORY-002), so not tested,
  consistent with how the rest of the codebase treats this class of "can't happen" state.
- Any account setting other than rating type.
