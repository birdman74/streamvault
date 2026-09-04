# STORY-016: Streaming Source per Library Item from a Predefined List

## Prerequisites
- story-007
- story-008
- story-009

## As a...
signed-in StreamVault user

## I want to...
label each movie or series in my library with the streaming service or source I use for it, chosen from a provided list

## So that...
I can see and filter my library by where each title lives

## Acceptance Criteria
- [ ] AC-1: StreamVault provides a predefined list of common streaming sources, available to every user (for example Netflix, Hulu, Max, Disney+, Prime Video, Apple TV+, Peacock, Paramount+)
- [ ] AC-2: A signed-in user can assign exactly one source from that list to a movie in their own library, and exactly one to a series in their own library
- [ ] AC-3: A user can change the assigned source, and can clear it, returning the item to having no source
- [ ] AC-4: The assigned source persists and is returned on subsequent reads of the item and in the library list
- [ ] AC-5: The library list from STORY-009 can be filtered by source, including an explicit "no source set" option, and this combines with the existing type and status filters
- [ ] AC-6: Source assignment is per user and per item; one user's assignment never affects another user's library
- [ ] AC-7: Assigning a source value that is not in the predefined list is rejected with a clear, user-facing error; custom sources are added in STORY-017
- [ ] AC-8: The predefined list is presented consistently to every user and is not editable by end users in this story

## Notes
- One source per item for this epic. Per-season or per-episode source (season 1 on one service, season 2 on another) is not in scope.
- The exact contents of the predefined list can be finalized by Test and Brian; AC-1's examples are a starting point, not a fixed set. The list should be stored as data, not hard-coded in a way that makes STORY-017 hard to add.
- "Source" covers both commercial streaming services and a user's own setups such as a home media server. Wording of the generic entries is a UI decision; STORY-017 is what lets a user add their own.

## Out of Scope
- User-created custom sources (STORY-017)
- Per-season or per-episode source
- Tracking availability windows, prices, or whether a title has left a service
- Deep links into the streaming service
