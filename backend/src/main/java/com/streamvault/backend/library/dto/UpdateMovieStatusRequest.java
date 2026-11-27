package com.streamvault.backend.library.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/library/movies/{id}/status}. {@code status} is required and
 * not blank; whether the non-blank value is one of the three supported statuses is a service-layer
 * concern so the rejection message can enumerate the valid options (same split as
 * {@code AddMovieRequest.status}).
 */
public record UpdateMovieStatusRequest(
        @NotBlank(message = "Status is required") String status) {
}
