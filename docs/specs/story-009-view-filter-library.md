# STORY-009: View and Filter My Library

## Prerequisites
- story-007
- story-008

## As a...
signed-in StreamVault user

## I want to...
see everything in my library in one place and narrow it down by type and status

## So that...
I can quickly find what I am currently watching, what I have finished, and what I plan to watch next

## Acceptance Criteria
- [ ] AC-1: A signed-in user can retrieve a list of every item in their own library, movies and series together
- [ ] AC-2: Each listed item shows at minimum its title, media type, poster reference, and current status; for a series the status shown is the rolled-up status per STORY-012
- [ ] AC-3: The list can be filtered to movies only, series only, or both
- [ ] AC-4: The list can be filtered by status (Planned, Currently Watching, Watched), including any combination of the three
- [ ] AC-5: Type and status filters can be applied together and return only items matching all active filters
- [ ] AC-6: The list is bounded per response (paginated or capped) so a large library never produces an unbounded response
- [ ] AC-7: A user only ever sees their own library; another user's items never appear regardless of filters
- [ ] AC-8: A user with an empty library, or a filter combination that matches nothing, receives an explicit empty result rather than an error

## Notes
- Sort order should be predictable and stable (for example most recently added first, or alphabetical). The exact default sort is a contract decision for Test, not a business requirement.
- Filtering by streaming source is added in STORY-016, not here.
- This story is read-only. Adding is STORY-007 and STORY-008; removing is STORY-011; status changes are STORY-010 and STORY-012.

## Out of Scope
- Filtering or grouping by streaming source (STORY-016), rating, or watch date
- Free-text search within the library
- Any write operation on library items
- Collections or named sub-libraries
