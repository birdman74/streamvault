package com.streamvault.backend.library.exception;

/**
 * Raised when {@code LibraryMovieService.updateStatus} finds no row matching both the given id and
 * the calling user - either the id does not exist at all, or it belongs to a different user. Both
 * cases carry this identical message so a caller cannot distinguish them (AC-5, AC-6). Mapped to 404
 * by {@code GlobalExceptionHandler}.
 */
public class LibraryMovieNotFoundException extends RuntimeException {

    public LibraryMovieNotFoundException() {
        super("This movie is not in your library.");
    }
}
