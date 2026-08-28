package com.streamvault.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleSignInRequest(

        @NotBlank(message = "ID token is required")
        String idToken
) {
    @Override
    public String toString() {
        return "GoogleSignInRequest[idToken=REDACTED]";
    }
}
