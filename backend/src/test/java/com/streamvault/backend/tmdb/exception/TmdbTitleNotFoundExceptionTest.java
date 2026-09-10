package com.streamvault.backend.tmdb.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Pins the fixed message
 * constant mapped to 404 by {@code GlobalExceptionHandler}, and that the exception retains the
 * {@code tmdbId} it was constructed with for logging / diagnostics.
 */
class TmdbTitleNotFoundExceptionTest {

    @Test
    void should_carryTheFixedUserFacingMessage_when_constructed() {
        assertThat(new TmdbTitleNotFoundException(27205L).getMessage())
                .isEqualTo("We could not find that movie on TMDB.");
    }

    @Test
    void should_retainTheTmdbId_when_constructed() {
        assertThat(new TmdbTitleNotFoundException(9_999_999L).getTmdbId()).isEqualTo(9_999_999L);
    }
}
