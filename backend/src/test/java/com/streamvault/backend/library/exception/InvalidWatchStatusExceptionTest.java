package com.streamvault.backend.library.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Mirrors
 * {@code InvalidRatingTypeExceptionTest}: the message names the offending value and lists the valid
 * options derived from {@code WatchStatus.values()} so a later enum change flows through
 * automatically, and {@code getInvalidValue()} returns the offending string.
 */
class InvalidWatchStatusExceptionTest {

    @Test
    void should_buildMessageNamingInvalidValueAndListingValidOptions_when_constructed() {
        InvalidWatchStatusException exception = new InvalidWatchStatusException("SOON");

        assertThat(exception.getMessage()).isEqualTo(
                "Invalid status 'SOON'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED.");
    }

    @Test
    void should_exposeInvalidValue_when_constructed() {
        InvalidWatchStatusException exception = new InvalidWatchStatusException("planned");

        assertThat(exception.getInvalidValue()).isEqualTo("planned");
    }
}
