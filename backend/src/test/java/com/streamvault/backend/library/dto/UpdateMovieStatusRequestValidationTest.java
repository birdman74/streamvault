package com.streamvault.backend.library.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Contract lives in docs/specs/design/story-010-api-contracts.md. UpdateMovieStatusRequest does
 * not exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Only the bean-validation ({@code @NotBlank}) concern is tested here, mirroring
 * {@code UpdateAccountSettingsRequestValidationTest}. Whether a non-blank value is one of the
 * three supported statuses is a service-layer concern per the contract, covered in
 * {@code LibraryMovieServiceTest} and {@code LibraryMovieControllerTest} instead.
 */
class UpdateMovieStatusRequestValidationTest {

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
    void should_acceptRequest_when_statusIsNonBlank() {
        UpdateMovieStatusRequest request = new UpdateMovieStatusRequest("WATCHED");

        Set<ConstraintViolation<UpdateMovieStatusRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_rejectRequest_when_statusIsBlank() {
        UpdateMovieStatusRequest request = new UpdateMovieStatusRequest("   ");

        Set<ConstraintViolation<UpdateMovieStatusRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("status"));
    }

    @Test
    void should_rejectRequest_when_statusIsNull() {
        UpdateMovieStatusRequest request = new UpdateMovieStatusRequest(null);

        Set<ConstraintViolation<UpdateMovieStatusRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("status"));
    }
}
