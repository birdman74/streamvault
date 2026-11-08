package com.streamvault.backend.library;

/**
 * The wire-facing discriminator for a merged {@code GET /api/library} list item. Kept distinct
 * from {@code com.streamvault.backend.tmdb.TmdbMediaType}: that enum models what TMDB's catalog
 * contains, this one models what a user's library contains, and the two are allowed to diverge
 * independently.
 */
public enum LibraryItemType {
    MOVIE,
    SERIES
}
