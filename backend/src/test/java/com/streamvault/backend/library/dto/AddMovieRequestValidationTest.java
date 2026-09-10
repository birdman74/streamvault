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
 * Contract lives in docs/specs/design/story-007-api-contracts.md. {@code AddMovieRequest} does not
 * exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Plain Jakarta {@link Validator}, no Spring context, mirrors {@code RegisterRequestValidationTest}.
 * Pins {@code @NotNull} / {@code @Positive} on {@code tmdbId} (AC-1) and that {@code status} is
 * deliberately NOT Bean-Validation-constrained: an arbitrary string produces no violation because the
 * service owns the "unsupported status" rejection with a clear message (AC-3, story-005 precedent).
 */
class AddMovieRequestValidationTest {

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
    void should_acceptRequest_when_tmdbIdIsPresentAndStatusOmitted() {
        AddMovieRequest request = new AddMovieRequest(27205L, null);

        Set<ConstraintViolation<AddMovieRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_acceptRequest_when_tmdbIdAndStatusAreBothProvided() {
        AddMovieRequest request = new AddMovieRequest(27205L, "PLANNED");

        Set<ConstraintViolation<AddMovieRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_rejectRequest_when_tmdbIdIsNull() {
        AddMovieRequest request = new AddMovieRequest(null, "PLANNED");

        Set<ConstraintViolation<AddMovieRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tmdbId"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -27205L})
    void should_rejectRequest_when_tmdbIdIsNotPositive(long tmdbId) {
        AddMovieRequest request = new AddMovieRequest(tmdbId, "PLANNED");

        Set<ConstraintViolation<AddMovieRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("tmdbId"));
    }

    @Test
    void should_notConstrainStatusStringValue_when_statusIsAnArbitraryString() {
        AddMovieRequest request = new AddMovieRequest(27205L, "NONSENSE_STATUS");

        Set<ConstraintViolation<AddMovieRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
