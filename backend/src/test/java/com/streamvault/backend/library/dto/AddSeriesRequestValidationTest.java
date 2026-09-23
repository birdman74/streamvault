package com.streamvault.backend.library.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. {@code AddSeriesRequest} does not
 * exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Plain Jakarta {@link Validator}, no Spring context, mirrors story-007's
 * {@code AddMovieRequestValidationTest}. Pins {@code @NotNull} / {@code @Positive} on the sole
 * {@code tmdbId} field. Unlike {@code AddMovieRequest} there is no {@code status} field to validate
 * or leave unconstrained — a series has no status choice at add time (AC-4).
 */
class AddSeriesRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void should_acceptRequest_when_tmdbIdIsPositive() {
        AddSeriesRequest request = new AddSeriesRequest(1399L);

        Set<ConstraintViolation<AddSeriesRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_rejectRequest_when_tmdbIdIsNull() {
        AddSeriesRequest request = new AddSeriesRequest(null);

        Set<ConstraintViolation<AddSeriesRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tmdbId"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -1399L})
    void should_rejectRequest_when_tmdbIdIsNotPositive(long tmdbId) {
        AddSeriesRequest request = new AddSeriesRequest(tmdbId);

        Set<ConstraintViolation<AddSeriesRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tmdbId"));
    }
}
