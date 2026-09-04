# STORY-005: Account Settings for Rating Type Preference

## Prerequisites
- None

## As a...
signed-in StreamVault user

## I want to...
choose which rating scale my account uses to rate titles

## So that...
the ratings I record match how I naturally think about scoring what I watch

## Acceptance Criteria
- [ ] AC-1: A signed-in user can view their current rating type preference in an account settings area
- [ ] AC-2: A user can change their rating type preference to any one of exactly three options: "Love / Like / Meh / Dislike / Hate", "Thumbs Up / Thumbs Down", "Half-star out of 5"
- [ ] AC-3: A newly registered account defaults to the "Love / Like / Meh / Dislike / Hate" rating type without the user taking any action
- [ ] AC-4: A changed rating type preference persists across sessions and is returned on every subsequent read of the user's settings
- [ ] AC-5: The rating type preference is per user; one user's change never affects another user's preference
- [ ] AC-6: A request to set the rating type to any value outside the three supported options is rejected with a clear, user-facing error and leaves the existing preference unchanged

## Notes
- This story deliberately covers only the rating type preference. All other account settings (change password, delete account, update profile or email) remain deferred to a future Account Settings epic, parked in docs/specs/backlog.md.
- STORY-015 (Personal Rating per Library Item) depends on this story: the rating type chosen here defines the set of values a user may assign to a title.
- What happens to ratings already recorded under a previous rating type when a user switches types is an open question. This story is not required to migrate, convert, or clear existing ratings, because STORY-015 is not built when this story is delivered. Test and Dev should not build conversion logic here. The open question is flagged in STORY-015 for resolution before that story is implemented.
- No changes to authentication, registration, or the JWT session format from STORY-001 and STORY-002 are in scope.

## Out of Scope
- Any account setting other than rating type
- Converting or re-scaling ratings recorded under a different rating type
- A user-defined or customizable rating scale (the three options above are fixed)
- UI design beyond the requirement that the setting is visible and changeable
