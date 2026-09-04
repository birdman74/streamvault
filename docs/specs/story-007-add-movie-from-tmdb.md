# STORY-007: Add Movie from TMDB to Library

## Prerequisites
- story-006

## As a...
signed-in StreamVault user

## I want to...
add a movie I found through TMDB search or browse to my personal library

## So that...
I can track that I have watched it, am watching it, or plan to watch it

## Acceptance Criteria
- [ ] AC-1: From a TMDB movie search or browse result, a signed-in user can add that movie to their own library
- [ ] AC-2: When a movie is added, StreamVault stores enough TMDB catalog data to display the library entry without a further TMDB call: at minimum TMDB id, title, release year, and poster reference
- [ ] AC-3: On add, the user sets the movie's status to one of Planned, Currently Watching, or Watched; if the user does not choose, the status defaults to Planned
- [ ] AC-4: A given TMDB movie can appear at most once in a single user's library; adding a movie already in that user's library returns a clear, user-facing error and creates no duplicate
- [ ] AC-5: The same TMDB movie can independently exist in different users' libraries, each with its own status and data
- [ ] AC-6: A user can only add to, and see, movies in their own library, never another user's
- [ ] AC-7: Adding a movie whose TMDB id TMDB does not recognize returns a clear, user-facing error and adds nothing

## Notes
- Status values Planned, Currently Watching, Watched are shared across the whole epic. For a movie the user sets status directly, because a movie has no lower level to roll up from. Changing status after add is STORY-010.
- Watch date, rating, notes, and streaming source are all added by later stories (STORY-013, STORY-015, STORY-014, STORY-016). This story only needs status.
- Whether "Currently Watching" is meaningful for a movie is left to the user; the system allows it.

## Out of Scope
- Editing status after the initial add (STORY-010)
- Removing a movie from the library (STORY-011)
- Any TV series behavior (STORY-008)
- Rating, notes, watch date, streaming source
