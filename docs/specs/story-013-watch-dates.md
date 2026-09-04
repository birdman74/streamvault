# STORY-013: Set and Clear Watch Dates Across Series Levels

## Prerequisites
- story-010
- story-012

## As a...
signed-in StreamVault user

## I want to...
record the date I watched something, and clear that date, at the movie, series, season, or episode level

## So that...
my library reflects when I watched things and I can correct mistakes

## Acceptance Criteria
- [ ] AC-1: A signed-in user can set a watch date on a movie in their own library
- [ ] AC-2: A signed-in user can set a watch date on a single episode of a series in their own library
- [ ] AC-3: A signed-in user can set a watch date at the season level and at the series level; watch dates at the movie, episode, season, and series levels are each stored independently, and setting a date at one level never changes a date stored at another level
- [ ] AC-4: A signed-in user can clear a previously set watch date independently at any of the four levels: movie, episode, season, series
- [ ] AC-5: Setting or clearing a watch date is restricted to items in the user's own library; a request against another user's item changes nothing
- [ ] AC-6: A watch date in the future, or a value that is not a valid date, is rejected with a clear, user-facing error and changes nothing
- [ ] AC-7: A set watch date persists and is returned on subsequent reads of the affected item, season, or episode
- [ ] AC-8: Clearing a watch date that was never set returns success and is a no-op, not an error

## Notes
- Clearing a watch date at the group level (across a user-defined collection) is out of scope because collections do not exist in this epic. The four levels in scope are movie, series, season, episode.
- Whether setting a watch date also implies a status change to Watched, or whether status and date stay independent, is a contract decision for Test. The business requirement is only that a date is settable and clearable at each of the four stated levels.
- A calendar date is sufficient; time of day is not required. Time zone handling for the "not in the future" check should follow the convention STORY-001 and STORY-002 already established for timestamps.

## Out of Scope
- Group or collection level watch dates
- A watch history of multiple dates per level; one current watch date per level is enough for this epic
- Automatic date stamping from any external source
