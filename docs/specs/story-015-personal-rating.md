# STORY-015: Personal Rating per Library Item

## Prerequisites
- story-005
- story-007
- story-008

## As a...
signed-in StreamVault user

## I want to...
give a movie or series in my library a personal rating using my account's rating scale

## So that...
I can record and later compare how much I liked what I watched

## Acceptance Criteria
- [ ] AC-1: A signed-in user can set a rating on a movie in their own library, using the rating type currently selected on their account (STORY-005)
- [ ] AC-2: A signed-in user can set a rating on a series in their own library, using the same account rating type
- [ ] AC-3: The accepted rating values match the account's rating type: one of the five values for "Love / Like / Meh / Dislike / Hate", one of the two values for "Thumbs Up / Thumbs Down", or a half-step value from 0.5 to 5 for "Half-star out of 5"
- [ ] AC-4: A rating value that is not valid for the account's current rating type is rejected with a clear, user-facing error and the stored rating is unchanged
- [ ] AC-5: A rating persists and is returned on subsequent reads of that library item
- [ ] AC-6: A user can change a rating any number of times, and can clear it, returning the item to unrated
- [ ] AC-7: Ratings are private to the owning user and per item
- [ ] AC-8: A rating can only be set on items in the user's own library; a request against another user's item changes nothing
- [ ] AC-9: Changing the account rating type (STORY-005) after ratings already exist does not alter, convert, or clear any stored rating; only rating writes made after the change are validated against the newly selected type

## Notes
- This story depends on STORY-005 because the account's rating type defines which values are valid for new writes here.
- Rating type switch behavior is decided (Brian, 2026-09-04): existing stored ratings are kept exactly as they are, and only new writes are validated against the current rating type. No conversion or clearing. AC-9 covers this.
- Ratings stay at the library-item level (a whole movie or a whole series). Per-season and per-episode ratings are deferred to the backlog.
- Whole-number star ratings (1, 2, 3, 4, 5) are valid values within the half-star scale, not a separate type.

## Out of Scope
- Per-season or per-episode ratings (backlog)
- Aggregate or average ratings across a user's library or across users
- A user-defined rating scale
- Converting or re-scaling stored ratings when the account rating type changes (decided against; see AC-9)
