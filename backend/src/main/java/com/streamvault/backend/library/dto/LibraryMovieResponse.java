package com.streamvault.backend.library.dto;

import java.time.Instant;

/**
 * The created library entry, carrying enough TMDB catalog data to render without another TMDB call
 * (AC-2). {@code status} is the stored {@code WatchStatus.name()} string, not the enum, so the JSON
 * value is always the plain string regardless of Jackson configuration (same reasoning as
 * story-005's {@code AccountSettingsResponse}). {@code releaseYear} and {@code posterUrl} are the
 * only nullable fields - null when TMDB itself does not provide them.
 */
public record LibraryMovieResponse(
        Long id,
        long tmdbId,
        String title,
        Integer releaseYear,
        String posterUrl,
        String status,
        Instant addedAt) {
}
