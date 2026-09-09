package com.streamvault.backend.settings.dto;

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
 * Contract lives in docs/specs/design/story-005-api-contracts.md. UpdateAccountSettingsRequest
 * does not exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * Only the bean-validation (@NotBlank) concern is tested here. Whether a non-blank value is one of
 * the three supported rating types is a service-layer concern per the contract, covered in
 * AccountSettingsServiceTest and AccountSettingsControllerTest instead.
 */
class UpdateAccountSettingsRequestValidationTest {

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
    void should_acceptRequest_when_ratingTypeIsNonBlank() {
        UpdateAccountSettingsRequest request = new UpdateAccountSettingsRequest("THUMBS_UP_THUMBS_DOWN");

        Set<ConstraintViolation<UpdateAccountSettingsRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_rejectRequest_when_ratingTypeIsBlank() {
        UpdateAccountSettingsRequest request = new UpdateAccountSettingsRequest("   ");

        Set<ConstraintViolation<UpdateAccountSettingsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ratingType"));
    }

    @Test
    void should_rejectRequest_when_ratingTypeIsNull() {
        UpdateAccountSettingsRequest request = new UpdateAccountSettingsRequest(null);

        Set<ConstraintViolation<UpdateAccountSettingsRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("ratingType"));
    }
}
