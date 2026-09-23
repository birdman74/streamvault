package com.streamvault.backend.library.exception;

/**
 * Raised when the calling user already has this TMDB movie in their library - either the
 * {@code existsByUserIdAndTmdbId} pre-check hit, or the {@code (user_id, tmdb_id)} unique constraint
 * rejected the insert on a race. Mapped to 409 by {@code GlobalExceptionHandler} (AC-4).
 */
public class DuplicateLibraryMovieException extends RuntimeException {

    public DuplicateLibraryMovieException() {
        super("This movie is already in your library.");
    }
}
