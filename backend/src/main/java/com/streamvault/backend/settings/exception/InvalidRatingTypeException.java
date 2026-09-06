package com.streamvault.backend.settings.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.streamvault.backend.user.RatingType;

public class InvalidRatingTypeException extends RuntimeException {

    private final String invalidValue;

    public InvalidRatingTypeException(String invalidValue) {
        super("Invalid rating type '" + invalidValue + "'. Valid options are: "
                + Arrays.stream(RatingType.values())
                        .map(RatingType::name)
                        .collect(Collectors.joining(", "))
                + ".");
        this.invalidValue = invalidValue;
    }

    public String getInvalidValue() {
        return invalidValue;
    }
}
