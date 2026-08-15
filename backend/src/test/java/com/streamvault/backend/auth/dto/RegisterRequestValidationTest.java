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

class RegisterRequestValidationTest {

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
    void acceptsAValidEmailAndPassword() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password1");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void rejectsAnInvalidEmail() {
        RegisterRequest request = new RegisterRequest("not-an-email", "Password1");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1A",       // under 8 characters
            "alllowercase1", // no uppercase
            "ALLUPPERCASE1", // no lowercase
            "NoDigitsHere",  // no number
    })
    void rejectsPasswordsThatFailComplexityRules(String password) {
        RegisterRequest request = new RegisterRequest("user@example.com", password);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void toStringNeverExposesThePasswordValue() {
        RegisterRequest request = new RegisterRequest("user@example.com", "Password1");

        assertThat(request.toString()).doesNotContain("Password1");
    }
}
