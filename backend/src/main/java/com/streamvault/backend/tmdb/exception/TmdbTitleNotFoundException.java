package com.streamvault.backend.tmdb.exception;

/**
 * Raised by the TMDB gateway only when {@code GET /movie/{id}} returns a {@code 404} - a definite
 * "no such movie" (unknown id, or an id that is not a movie). Distinct from
 * {@link TmdbUnavailableException}, which covers a TMDB outage (5xx / transport / 429 / bad-key
 * 401). Mapped to 404 by {@code GlobalExceptionHandler} (AC-7). Lives in the {@code tmdb} package
 * and references no persistence API, so the {@code TmdbPackageReadOnlyConventionTest} source scan
 * still passes.
 */
public class TmdbTitleNotFoundException extends RuntimeException {

    private final long tmdbId;

    public TmdbTitleNotFoundException(long tmdbId) {
        super("We could not find that movie on TMDB.");
        this.tmdbId = tmdbId;
    }

    public long getTmdbId() {
        return tmdbId;
    }
}
