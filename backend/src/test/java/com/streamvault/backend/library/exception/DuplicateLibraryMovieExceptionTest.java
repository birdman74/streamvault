package com.streamvault.backend.library.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Pins the fixed user-facing
 * message constant that {@code GlobalExceptionHandler} echoes into the 409 body.
 */
class DuplicateLibraryMovieExceptionTest {

    @Test
    void should_carryTheFixedUserFacingMessage_when_constructed() {
        assertThat(new DuplicateLibraryMovieException().getMessage())
                .isEqualTo("This movie is already in your library.");
    }
}
