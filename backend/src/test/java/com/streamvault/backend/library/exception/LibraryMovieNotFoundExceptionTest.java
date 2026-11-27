package com.streamvault.backend.library.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contract lives in docs/specs/design/story-010-api-contracts.md. LibraryMovieNotFoundException
 * does not exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Mirrors {@code DuplicateLibraryMovieExceptionTest}: pins the fixed user-facing message
 * constant that {@code GlobalExceptionHandler} echoes into the 404 body (AC-5, AC-6).
 */
class LibraryMovieNotFoundExceptionTest {

    @Test
    void should_carryTheFixedUserFacingMessage_when_constructed() {
        assertThat(new LibraryMovieNotFoundException().getMessage())
                .isEqualTo("This movie is not in your library.");
    }
}
