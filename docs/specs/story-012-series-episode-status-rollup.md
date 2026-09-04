# STORY-012: Series Progress by Episode with Season and Series Roll-Up

## Prerequisites
- story-008

## As a...
signed-in StreamVault user

## I want to...
mark individual episodes of a series as Planned, Currently Watching, or Watched, and also update a whole season or the whole series in one action

## So that...
my series progress stays accurate without my having to click through every single episode

## Acceptance Criteria
- [ ] AC-1: A signed-in user can set the status of a single episode of a series in their own library to Planned, Currently Watching, or Watched
- [ ] AC-2: A season's status is always derived, never set by hand: Watched when every episode in that season is Watched, Planned when every episode is Planned, and Currently Watching in every other case (any mix of statuses, or any episode Currently Watching)
- [ ] AC-3: A series' status is derived from its seasons by the same rule: Watched when every season is Watched, Planned when every season is Planned, Currently Watching otherwise
- [ ] AC-4: A user can apply a status to an entire season in one action; this sets every episode in that season to that status, and the season and series roll-up updates accordingly
- [ ] AC-5: A user can apply a status to the entire series in one action; this sets every episode in every season to that status
- [ ] AC-6: Immediately after any change, whether to one episode, a season, or the series, the derived season and series statuses are consistent with the underlying episode statuses
- [ ] AC-7: A user can only change progress on series in their own library; a request against another user's series changes nothing
- [ ] AC-8: A request naming an episode or season that does not belong to the series, or a series not in the user's library, returns a clear, user-facing error and changes nothing
- [ ] AC-9: An invalid status value is rejected with a clear, user-facing error and changes nothing

## Notes
- The roll-up rules in AC-2 and AC-3 are the product decision. Brian's rule: the user only ever sets status at the lowest level (the episode), or uses a bulk action as a shortcut for setting many episodes at once. Season and series statuses are read-only derivations.
- A season or series with zero episodes must not break roll-up; treat an empty grouping as Planned. This should be rare because STORY-008 stores the full structure.
- "Currently Watching" as a rolled-up value is expected to be the common case for a series in progress. It does not require any episode to be explicitly Currently Watching, only that episodes are not all in the same terminal state.
- Watch date interaction (for example clearing dates when status moves away from Watched) is handled in STORY-013, not here.

## Out of Scope
- Watch date set or clear (STORY-013)
- Ratings or notes at the episode or season level (item level only, and deferred to the backlog)
- Re-syncing new seasons or episodes added on TMDB after the series was added
