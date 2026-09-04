# STORY-017: Custom Streaming Source Entries

## Prerequisites
- story-016

## As a...
signed-in StreamVault user

## I want to...
add my own streaming sources when the one I use is not in the provided list

## So that...
I can label titles I watch on services or setups StreamVault does not list, such as a personal Plex server

## Acceptance Criteria
- [ ] AC-1: A signed-in user can create a custom source by name, which then becomes available to assign to their own library items alongside the predefined list
- [ ] AC-2: Custom sources are private to the user who created them; they never appear in another user's list of available sources
- [ ] AC-3: A user can rename or delete a custom source they created
- [ ] AC-4: Deleting a custom source that is currently assigned to one or more of the user's items clears the source on those items so they revert to having no source; it does not delete the items
- [ ] AC-5: A custom source name has a defined maximum length and cannot be blank; violations are rejected with a clear, user-facing error
- [ ] AC-6: A user cannot create a custom source whose name duplicates one of their existing sources, predefined or custom, compared case-insensitively; the attempt is rejected with a clear, user-facing error
- [ ] AC-7: Assigning a custom source to a movie or series behaves exactly as assigning a predefined one in STORY-016, including one source per item and appearing in the STORY-009 source filter
- [ ] AC-8: A user cannot edit or delete predefined sources through this story; custom sources are a per-user addition, not a way to change the shared list

## Notes
- This story was split from STORY-016 on purpose so the predefined-list behavior can ship and be tested first.
- Whether a user's custom source that later exactly matches a newly added predefined source should be de-duplicated is an edge case; a reasonable default is to leave both and let the user delete their custom one. Not a blocker.
- No sharing, discovery, or promotion of custom sources between users.

## Out of Scope
- Any admin flow for changing the predefined list
- Per-season or per-episode source
- Icons or branding for custom sources
- Merging or bulk reassigning items from one source to another
