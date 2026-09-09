package com.streamvault.backend.settings.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidRatingTypeExceptionTest {

    @Test
    void should_buildMessageNamingInvalidValueAndListingValidOptions_when_constructed() {
        InvalidRatingTypeException exception = new InvalidRatingTypeException("FIVE_STARS");

        assertThat(exception.getMessage()).isEqualTo(
                "Invalid rating type 'FIVE_STARS'. Valid options are: "
                        + "LOVE_LIKE_MEH_DISLIKE_HATE, THUMBS_UP_THUMBS_DOWN, HALF_STAR_OUT_OF_5.");
    }

    @Test
    void should_exposeInvalidValue_when_constructed() {
        InvalidRatingTypeException exception = new InvalidRatingTypeException("FIVE_STARS");

        assertThat(exception.getInvalidValue()).isEqualTo("FIVE_STARS");
    }
}
