# Epic: Personal Streaming Library

## Goal
Give each StreamVault user a private library of movies and TV series sourced from The Movie Database (TMDB). Users search TMDB, add titles to their own library, and track progress against three states: Planned, Currently Watching, Watched. TV series are tracked down to the individual episode, with season and series status derived automatically from episode progress. Users can record a personal rating, a free-text note, a watch date, and which streaming service or source they use for each title. This epic delivers the core product value StreamVault exists to demonstrate: a personal, per-user record of what someone has watched and plans to watch, backed by real catalog data.

## Stories
- STORY-006: TMDB Search and Browse
- STORY-007: Add Movie from TMDB to Library
- STORY-008: Add TV Series from TMDB to Library
- STORY-009: View and Filter My Library
- STORY-010: Set Movie Watch Status
- STORY-011: Remove Item from Library
- STORY-012: Series Progress by Episode with Season and Series Roll-Up
- STORY-013: Set and Clear Watch Dates Across Series Levels
- STORY-014: Notes and Review per Library Item
- STORY-015: Personal Rating per Library Item
- STORY-016: Streaming Source per Library Item from a Predefined List
- STORY-017: Custom Streaming Source Entries
- STORY-018: Refresh a Series from TMDB to Pick Up New Seasons and Episodes
- STORY-019: Bypass Removal Confirmation Preference

## Prerequisite Outside This Epic
- STORY-005: Account Settings for Rating Type Preference. Required before STORY-015. Specced and tracked as its own story and GitHub Issue, but not counted as an epic story because account settings are a separate product concern. A future Account Settings epic will cover change-password, delete-account, and profile or email updates (parked in docs/specs/backlog.md).

## Data Store Direction (note for Dev)
Every entity in this epic is structured, predictable data: TMDB movie and series records, per-user library rows, per-episode progress, ratings drawn from a fixed set, watch dates, and a source label. All of it belongs in PostgreSQL. Do not introduce MongoDB anywhere in this epic. MongoDB enters the project only when users begin entering custom, variable-schema data that a relational schema cannot model cleanly, for example user-authored metadata for home movies or other non-TMDB entries. That work is not in this epic.

## Out of Scope (see docs/specs/backlog.md)
- Grouping and collections (MCU, Pixar, Zatoichi, multiple named libraries). Deferred to its own future epic. Because groups do not exist in this epic, clearing a watch date at the group level is also deferred; STORY-013 covers movie, series, season, and episode levels only.
- Per-season and per-episode notes and ratings. This epic keeps notes and ratings at the library-item level (a movie or a whole series).
- A user-customizable rating scale. The three rating types in STORY-005 are the only options.
- Non-TMDB catalog sources and manual title entry.
- Discovery or recommendation features beyond TMDB's own search and basic browse lists.
- User-authored custom metadata and tags on library items (would feed the STORY-009 filters); parked in docs/specs/backlog.md as a follow-up.
- An always-on background scheduler that refreshes libraries independent of user activity. STORY-018 covers user-triggered and staleness-triggered refresh only; a dedicated scheduled job is parked in docs/specs/backlog.md against the project's cost constraints.
