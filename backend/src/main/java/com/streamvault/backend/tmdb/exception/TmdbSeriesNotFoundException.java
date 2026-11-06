package com.streamvault.backend.tmdb.exception;

/**
 * Raised by the TMDB gateway only when {@code GET /tv/{id}} returns a {@code 404} - a definite
 * "no such series" (unknown id, or an id that is a movie rather than a series). Distinct from
 * {@link TmdbUnavailableException}, which covers a TMDB outage (5xx / transport / 429 / bad-key
 * 401) as well as any failure on a later batched season-episode call, since the series id is
 * already confirmed valid by that point. Distinct class and message from
 * {@link TmdbTitleNotFoundException} (story-007, movie-specific). Mapped to 404 by
 * {@code GlobalExceptionHandler} (AC-8). Lives in the {@code tmdb} package and references no
 * persistence API, so the {@code TmdbPackageReadOnlyConventionTest} source scan still passes.
 */
public class TmdbSeriesNotFoundException extends RuntimeException {

    private final long tmdbId;

    public TmdbSeriesNotFoundException(long tmdbId) {
        super("We could not find that series on TMDB.");
        this.tmdbId = tmdbId;
    }

    public long getTmdbId() {
        return tmdbId;
    }
}
