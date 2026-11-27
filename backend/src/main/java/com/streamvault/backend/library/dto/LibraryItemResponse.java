package com.streamvault.backend.library.dto;

import java.time.Instant;

/**
 * One item in the merged {@code GET /api/library} list (AC-2). {@code type} and {@code status}
 * are plain constant-name strings ({@code LibraryItemType.name()} / {@code WatchStatus.name()}),
 * not the enum, so the JSON value is always the plain string regardless of Jackson configuration,
 * same reasoning as {@code LibraryMovieResponse.status}. {@code id} is unique only combined with
 * {@code type}: {@code library_movies.id} and {@code library_series.id} are independent
 * {@code BIGSERIAL} sequences and can collide. {@code year} unifies a movie's {@code releaseYear}
 * and a series' {@code firstAirYear} into one field.
 */
public record LibraryItemResponse(
        Long id,
        String type,
        long tmdbId,
        String title,
        Integer year,
        String posterUrl,
        String status,
        Instant addedAt) {
}
