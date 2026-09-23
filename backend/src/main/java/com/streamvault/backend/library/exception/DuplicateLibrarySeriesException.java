package com.streamvault.backend.library.exception;

/**
 * Raised when the calling user already has this TMDB series in their library - either the
 * {@code existsByUserIdAndTmdbId} pre-check hit, or the {@code (user_id, tmdb_id)} unique constraint
 * rejected the insert on a race. Mapped to 409 by {@code GlobalExceptionHandler} (AC-5).
 */
public class DuplicateLibrarySeriesException extends RuntimeException {

    public DuplicateLibrarySeriesException() {
        super("This series is already in your library.");
    }
}
