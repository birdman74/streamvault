package com.streamvault.backend.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAccountSettingsRequest(
        @NotBlank(message = "Rating type is required") String ratingType) {
}
