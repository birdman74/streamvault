# STORY-011: Remove Item from Library

## Prerequisites
- story-007
- story-008

## As a...
signed-in StreamVault user

## I want to...
remove a movie or series from my library

## So that...
titles I added by mistake or no longer care to track do not clutter my library

## Acceptance Criteria
- [ ] AC-1: A signed-in user can remove a movie from their own library
- [ ] AC-2: A signed-in user can remove a series from their own library, which also removes all of that series' stored season and episode progress for that user
- [ ] AC-3: After removal the item no longer appears in the library list from STORY-009 for that user
- [ ] AC-4: Removing an item deletes only that user's copy; the same TMDB title in any other user's library is untouched
- [ ] AC-5: A user can only remove items from their own library; a request against another user's item is rejected and removes nothing
- [ ] AC-6: Removing an item that is not in the user's library returns a clear, user-facing error
- [ ] AC-7: After removing a title, a user can add the same TMDB title again as a fresh library entry with default status Planned and no prior rating, note, watch date, or source
- [ ] AC-8: Removal requires an explicit confirmation step; a remove request that has not been confirmed deletes nothing. (STORY-019 adds a per-account preference to bypass this step; absent that preference the confirmation is always required.)

## Notes
- Removal is permanent for this epic. An archive or hide-without-deleting concept is not part of this story.
- The confirmation step in AC-8 is a hard requirement, not a UI nicety. The exact presentation (dialog, typed confirmation, second click) is a UI decision, but some deliberate confirmation must stand between the request and the delete.
- The ability to switch that confirmation off is deliberately its own story (STORY-019) so this story ships the safe default first.
- Any rating, note, watch date, or source attached to the item is removed with it; AC-7 confirms a re-add starts clean.

## Out of Scope
- Undo, or a trash and restore flow
- Bulk removal of multiple items in one action
- Removing individual seasons or episodes while keeping the rest of the series; a series is added and removed as a whole
- The per-account preference to bypass the confirmation step (STORY-019)
