package com.streamvault.backend.tmdb.dto;

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
 * Contract lives in docs/specs/design/story-006-api-contracts.md. {@code TmdbSearchRequest} does
 * not exist yet; this test is expected to fail to compile until Dev implements it.
 *
 * <p>Mirrors {@code RegisterRequestValidationTest}: pins only the Bean Validation concerns on the
 * search value object -- {@code @NotBlank} on {@code query} and the {@code @Min(1)} / {@code @Max(500)}
 * page bounds that keep every response bounded (AC-4). A {@code null} page means "page 1" and must
 * be accepted.
 */
class TmdbSearchRequestValidationTest {

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
    void should_acceptRequest_when_queryIsPresentAndPageIsNull() {
        Set<ConstraintViolation<TmdbSearchRequest>> violations =
                validator.validate(new TmdbSearchRequest("inception", null));

        assertThat(violations).isEmpty();
    }

    @Test
    void should_acceptRequest_when_pageIsWithinBounds() {
        assertThat(validator.validate(new TmdbSearchRequest("inception", 1))).isEmpty();
        assertThat(validator.validate(new TmdbSearchRequest("inception", 500))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void should_rejectRequest_when_queryIsBlank(String query) {
        Set<ConstraintViolation<TmdbSearchRequest>> violations =
                validator.validate(new TmdbSearchRequest(query, 1));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("query"));
    }

    @Test
    void should_rejectRequest_when_queryIsNull() {
        Set<ConstraintViolation<TmdbSearchRequest>> violations =
                validator.validate(new TmdbSearchRequest(null, 1));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("query"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -50, 501, 1000})
    void should_rejectRequest_when_pageIsOutsideTheOneToFiveHundredRange(int page) {
        Set<ConstraintViolation<TmdbSearchRequest>> violations =
                validator.validate(new TmdbSearchRequest("inception", page));

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("page"));
    }
}
