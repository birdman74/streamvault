package com.streamvault.backend.library.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/library/movies}. {@code tmdbId} is required and must be positive;
 * a missing, null, zero, or negative value is a 400 before any TMDB or DB call. {@code status} is a
 * raw nullable {@code String} deliberately NOT constrained by Bean Validation - the service owns the
 * "unsupported status" rejection with a clear enumerated message (AC-3, story-005 precedent). The
 * record has no {@code userId} component, so a client that sends one is ignored; the owning user is
 * always taken from the JWT principal (AC-6).
 */
public record AddMovieRequest(
        @NotNull @Positive Long tmdbId,
        String status) {
}
