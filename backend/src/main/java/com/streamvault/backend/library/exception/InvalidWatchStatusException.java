package com.streamvault.backend.library.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.streamvault.backend.library.WatchStatus;

/**
 * Raised by {@code LibraryMovieService} when the request carries a {@code status} string that is not
 * one of the {@link WatchStatus} constants (matched case-sensitively). Mapped to 400 by
 * {@code GlobalExceptionHandler}. Message format mirrors {@code InvalidRatingTypeException}: the
 * valid-options list is derived from {@code WatchStatus.values()} so a later enum change flows
 * through automatically.
 */
public class InvalidWatchStatusException extends RuntimeException {

    private final String invalidValue;

    public InvalidWatchStatusException(String invalidValue) {
        super("Invalid status '" + invalidValue + "'. Valid options are: "
                + Arrays.stream(WatchStatus.values())
                        .map(WatchStatus::name)
                        .collect(Collectors.joining(", "))
                + ".");
        this.invalidValue = invalidValue;
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}
