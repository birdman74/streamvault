# STORY-006: TMDB Search and Browse

## Prerequisites
- None

## As a...
signed-in StreamVault user

## I want to...
search The Movie Database for movies and TV series, and browse common lists such as popular or trending titles

## So that...
I can find the exact title I want before adding it to my library

## Acceptance Criteria
- [ ] AC-1: A signed-in user can enter a free-text query and receive matching movie and TV series results sourced from TMDB
- [ ] AC-2: Each result shows enough information to tell similarly named titles apart: title, media type (movie or series), release or first-air year, and poster image where TMDB provides one
- [ ] AC-3: A user can browse at least one curated list without entering a query (for example TMDB popular or trending titles), returning the same shape of result data as search
- [ ] AC-4: Search and browse results are paginated or bounded so a single response never returns an unbounded result set
- [ ] AC-5: A query that matches nothing returns an explicit empty result, not an error
- [ ] AC-6: When TMDB is unreachable or returns an error, the user sees a clear, user-facing message and the rest of the application keeps working
- [ ] AC-7: Search and browse are read-only against TMDB; this story writes nothing to any user's library
- [ ] AC-8: Only signed-in users can search or browse; unauthenticated requests are rejected consistently with the rest of the API

## Notes
- The TMDB API key and base URL are already provisioned as environment variables (TMDB_API_KEY, TMDB_BASE_URL in .env.example). No new credentials or infrastructure are needed.
- TMDB attribution (the "powered by TMDB" acknowledgement their terms require) should appear somewhere in the UI. Exact placement is a UI decision, not an acceptance criterion.
- Result caching to limit TMDB call volume is allowed but not required. If added, it must not present one user with results that misrepresent what TMDB currently returns.
- This story does not define how a result becomes a library item; that is STORY-007 for movies and STORY-008 for series.

## Out of Scope
- Adding anything to a library (STORY-007, STORY-008)
- Season and episode detail in results (pulled later, when a series is added, in STORY-008)
- Personalized recommendations, "because you watched" discovery, or genre filtering beyond what a plain TMDB search and one browse list provide
- Non-TMDB sources
