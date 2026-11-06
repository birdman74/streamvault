package com.streamvault.backend.tmdb.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-008-agreed.md "Test Coverage Confirmation"). Mirrors
 * {@code TmdbTitleNotFoundExceptionTest} (story-007): pins the fixed message constant mapped to
 * 404 by {@code GlobalExceptionHandler}, and that the exception retains the {@code tmdbId} it was
 * constructed with for logging / diagnostics.
 */
class TmdbSeriesNotFoundExceptionTest {

    @Test
    void should_carryTheFixedUserFacingMessage_when_constructed() {
        assertThat(new TmdbSeriesNotFoundException(1399L).getMessage())
                .isEqualTo("We could not find that series on TMDB.");
    }

    @Test
    void should_retainTheTmdbId_when_constructed() {
        assertThat(new TmdbSeriesNotFoundException(9_999_999L).getTmdbId()).isEqualTo(9_999_999L);
    }
}
