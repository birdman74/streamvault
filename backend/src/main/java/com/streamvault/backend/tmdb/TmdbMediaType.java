package com.streamvault.backend.tmdb;

/**
 * Wire-facing media type for a TMDB catalog result. The JSON value is the constant name
 * ({@code MOVIE} / {@code SERIES}), matched exactly, mirroring the story-005 {@code RatingType}
 * precedent. TMDB's own {@code media_type} strings ({@code movie} / {@code tv}) are mapped onto
 * these by {@link RestClientTmdbGateway}; every other TMDB {@code media_type} (e.g. {@code person})
 * is dropped before a response is built.
 */
public enum TmdbMediaType {
    MOVIE,
    SERIES
}
