package com.streamvault.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Password must be at least 8 characters and contain at least one uppercase letter, "
                        + "one lowercase letter, and one number"
        )
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=REDACTED]";
    }
}
