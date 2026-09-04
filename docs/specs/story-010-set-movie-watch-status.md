# STORY-010: Set Movie Watch Status

## Prerequisites
- story-007

## As a...
signed-in StreamVault user

## I want to...
change the status of a movie in my library between Planned, Currently Watching, and Watched

## So that...
my library reflects what I have actually watched over time

## Acceptance Criteria
- [ ] AC-1: A signed-in user can set the status of a movie in their own library to Planned, Currently Watching, or Watched
- [ ] AC-2: The new status persists and is returned on subsequent reads of that library item and in the library list from STORY-009
- [ ] AC-3: Status can be changed any number of times and in any direction, including Watched back to Planned
- [ ] AC-4: A status value outside the three allowed values is rejected with a clear, user-facing error and leaves the existing status unchanged
- [ ] AC-5: A user can only change the status of movies in their own library; a request against another user's item is rejected and changes nothing
- [ ] AC-6: Setting status on a movie that is not in the user's library returns a clear, user-facing error

## Notes
- This story is movies only. Series status is never set directly; it is derived from episode progress in STORY-012.
- Setting status to Watched here does not by itself require a watch date; recording and clearing watch dates is STORY-013.
- No rating or note side effects; those are independent (STORY-014, STORY-015).

## Out of Scope
- Series, season, or episode status (STORY-012)
- Watch date behavior on status change (STORY-013)
- Bulk status changes across multiple library items at once
