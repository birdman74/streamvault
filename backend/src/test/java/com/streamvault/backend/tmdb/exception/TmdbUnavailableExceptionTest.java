package com.streamvault.backend.tmdb.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Dev unit test for {@link TmdbUnavailableException}. The {@code (Throwable)} constructor must retain
 * the cause so {@code GlobalExceptionHandler} can log it (project "no swallowed exceptions" rule),
 * and the {@code (String)} constructor path used by the controller tests must work without a cause
 * (per {@code story-006-agreed.md} "Test Coverage Confirmation").
 */
class TmdbUnavailableExceptionTest {

    @Test
    void throwableConstructor_retainsTheCause() {
        IOException cause = new IOException("connection reset");

        TmdbUnavailableException ex = new TmdbUnavailableException(cause);

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isNotBlank();
    }

    @Test
    void stringConstructor_setsTheMessageAndHasNoCause() {
        TmdbUnavailableException ex = new TmdbUnavailableException("upstream 503");

        assertThat(ex.getMessage()).isEqualTo("upstream 503");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void isARuntimeException() {
        assertThat(new TmdbUnavailableException("x")).isInstanceOf(RuntimeException.class);
    }
}
