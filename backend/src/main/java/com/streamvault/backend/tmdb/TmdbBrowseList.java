package com.streamvault.backend.tmdb;

/**
 * The curated lists {@code GET /api/tmdb/browse} can serve. Matched exactly (case-sensitive) by
 * the default Spring {@code String} to enum binding, consistent with the story-005 {@code RatingType}
 * precedent; an unrecognized {@code list} value is a 400. {@code POPULAR} is the default when the
 * caller omits {@code list}.
 */
public enum TmdbBrowseList {
    POPULAR,
    TRENDING
}
