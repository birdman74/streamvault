package com.streamvault.backend.library.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.streamvault.backend.library.LibraryItemType;
import com.streamvault.backend.library.WatchStatus;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. {@code LibraryListRequest} does
 * not exist yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Plain Jakarta {@link Validator}, no Spring context, mirrors {@code AddMovieRequestValidationTest}.
 * Pins {@code @Min(1)} on {@code page} and {@code @Min(1) @Max(100)} on {@code pageSize}, that both
 * are optional ({@code null} produces no violation, mirroring {@code TmdbSearchRequest.page}'s
 * precedent), and that {@code type} / {@code status} carry no Bean Validation constraint of their
 * own - an unrecognized value in either is a binding failure at the HTTP layer, not a Bean
 * Validation concern at this layer, mirroring {@code TmdbBrowseRequest.list}'s precedent.
 */
class LibraryListRequestValidationTest {

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
    void should_acceptRequest_when_everyFieldIsOmitted() {
        LibraryListRequest request = new LibraryListRequest(null, null, null, null);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_acceptRequest_when_everyFieldIsPresentAndValid() {
        LibraryListRequest request = new LibraryListRequest(
                List.of(LibraryItemType.MOVIE, LibraryItemType.SERIES),
                List.of(WatchStatus.PLANNED, WatchStatus.WATCHED),
                2, 50);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void should_acceptRequest_when_typeAndStatusAreEmptyLists() {
        LibraryListRequest request = new LibraryListRequest(List.of(), List.of(), 1, 20);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void should_rejectRequest_when_pageIsLessThanOne(int page) {
        LibraryListRequest request = new LibraryListRequest(null, null, page, 20);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("page"));
    }

    @Test
    void should_acceptRequest_when_pageIsExactlyOne() {
        LibraryListRequest request = new LibraryListRequest(null, null, 1, 20);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void should_rejectRequest_when_pageSizeIsLessThanOne(int pageSize) {
        LibraryListRequest request = new LibraryListRequest(null, null, 1, pageSize);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("pageSize"));
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 500})
    void should_rejectRequest_when_pageSizeExceedsOneHundred(int pageSize) {
        LibraryListRequest request = new LibraryListRequest(null, null, 1, pageSize);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("pageSize"));
    }

    @Test
    void should_acceptRequest_when_pageSizeIsExactlyOneHundred() {
        LibraryListRequest request = new LibraryListRequest(null, null, 1, 100);

        Set<ConstraintViolation<LibraryListRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
