# STORY-018: Refresh a Series from TMDB to Pick Up New Seasons and Episodes

## Prerequisites
- story-008
- story-012

## As a...
signed-in StreamVault user

## I want to...
refresh a series in my library against TMDB so newly aired seasons and episodes appear, and optionally have that happen automatically

## So that...
my progress tracking stays complete for shows that are still running

## Acceptance Criteria
- [ ] AC-1: A signed-in user can trigger a refresh of a single series in their own library; the refresh compares the stored season and episode structure against TMDB and adds any seasons or episodes that now exist on TMDB but are missing locally
- [ ] AC-2: Seasons and episodes added by a refresh start with status Planned and are immediately included in season and series roll-up per STORY-012
- [ ] AC-3: A refresh never changes the user's existing status, watch date, rating, note, or source on seasons and episodes that were already present
- [ ] AC-4: A refresh updates stored catalog data such as an episode title that was previously blank, without touching any user progress data
- [ ] AC-5: Seasons or episodes present locally but no longer on TMDB are left in place and are not deleted, so user progress is never lost to a TMDB data change
- [ ] AC-6: A user can set a per-account preference to have their series refreshed automatically; the default is off, meaning refresh only happens when the user triggers it
- [ ] AC-7: When the automatic preference is on, a refresh happens without a manual trigger on a defined, documented cadence, for example the next time the user opens the series when its data is older than a set staleness window; an always-on background scheduler is out of scope for this story
- [ ] AC-8: A refresh is restricted to series in the user's own library and never touches another user's copy of the same series
- [ ] AC-9: If TMDB is unreachable or errors during a refresh, the user sees a clear message, nothing in their library changes, and the rest of the app keeps working

## Notes
- This story exists because STORY-008 snapshots the season and episode tree at add time and never updates it afterward.
- AC-7 keeps the automatic path cheap on purpose: a staleness-triggered refresh on access, not a job iterating every user's whole library. A true scheduled background sync is parked in docs/specs/backlog.md against the project's cost constraints.
- The automatic-refresh preference is a second per-account setting alongside STORY-005's rating type. Both are being carved out individually ahead of a full Account Settings epic.

## Out of Scope
- An always-on background scheduler or job queue that refreshes libraries independent of user activity (backlog)
- Refreshing movies; movie catalog data does not gain structure over time the way a running series does
- Notifying the user that new episodes aired
- A single "refresh my entire library" bulk action
