# STORY-008: Add TV Series from TMDB to Library

## Prerequisites
- story-006

## As a...
signed-in StreamVault user

## I want to...
add a TV series I found through TMDB search or browse to my personal library, including its season and episode structure

## So that...
I can track my progress through the series at the episode level

## Acceptance Criteria
- [ ] AC-1: From a TMDB series search or browse result, a signed-in user can add that series to their own library
- [ ] AC-2: When a series is added, StreamVault stores the series' season and episode structure from TMDB: every season and, within each season, every episode, each identified and labeled (season number, episode number, and episode title where TMDB provides one)
- [ ] AC-3: When a series is added, enough series-level TMDB catalog data is stored to display the entry without a further TMDB call: at minimum TMDB id, title, first-air year, and poster reference
- [ ] AC-4: On add, every episode of the series starts with status Planned, so the series and every season roll up to Planned (the roll-up computation itself is specified in STORY-012)
- [ ] AC-5: A given TMDB series can appear at most once in a single user's library; adding a series already present returns a clear, user-facing error and creates no duplicate
- [ ] AC-6: The same TMDB series can independently exist in different users' libraries, each with its own progress
- [ ] AC-7: A user can only add to, and see, series in their own library, never another user's
- [ ] AC-8: Adding a series whose TMDB id TMDB does not recognize returns a clear, user-facing error and adds nothing
- [ ] AC-9: Specials or episodes TMDB places outside a numbered season (commonly "season 0") are handled explicitly and the same way every time: either stored consistently as their own season grouping, or excluded

## Notes
- This is a deliberately larger story than STORY-007 because it pulls and persists the full season and episode tree at add time.
- Setting and rolling up status is STORY-012; this story only needs the initial Planned state on every episode plus the stored structure.
- Handling a series that gains new seasons or episodes on TMDB after the user has added it (re-sync) is an open question deferred to a later story; this story is not required to re-sync.
- Watch date, rating, notes, and streaming source come from later stories.

## Out of Scope
- Setting or changing episode, season, or series status (STORY-012)
- Re-syncing structure when TMDB adds later seasons or episodes
- Removing a series from the library (STORY-011)
- Rating, notes, watch date, streaming source
