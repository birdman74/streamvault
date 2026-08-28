package com.streamvault.backend.auth.dto;

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

class GoogleSignInRequestValidationTest {

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
    void acceptsANonBlankIdToken() {
        GoogleSignInRequest request = new GoogleSignInRequest("a-valid-looking-id-token");

        Set<ConstraintViolation<GoogleSignInRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsABlankIdToken(String idToken) {
        GoogleSignInRequest request = new GoogleSignInRequest(idToken);

        Set<ConstraintViolation<GoogleSignInRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idToken"));
    }

    @Test
    void rejectsANullIdToken() {
        GoogleSignInRequest request = new GoogleSignInRequest(null);

        Set<ConstraintViolation<GoogleSignInRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("idToken"));
    }
}
