package com.streamvault.backend.library.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/library/series}. {@code tmdbId} is required and must be
 * positive; a missing, null, zero, or negative value is a 400 before any TMDB or DB call. No
 * {@code status} field - a series has no choice to make at add time (AC-4). The record has no
 * {@code userId} component, so a client that sends one is ignored; the owning user is always taken
 * from the JWT principal (AC-7).
 */
public record AddSeriesRequest(@NotNull @Positive Long tmdbId) {
}
