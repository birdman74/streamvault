package com.streamvault.backend.tmdb.dto;

/**
 * One catalog result, identical in shape for search and browse (AC-3).
 *
 * <p>{@code mediaType} is {@code TmdbMediaType.name()} ({@code "MOVIE"} / {@code "SERIES"}), not the
 * enum, so the JSON value is always the plain string regardless of Jackson configuration (same
 * reasoning as story-005's {@code AccountSettingsResponse}). {@code releaseYear} and {@code posterUrl}
 * are the only nullable fields: {@code releaseYear} is {@code null} when TMDB omits / empties / sends
 * an unparseable date, {@code posterUrl} is {@code null} when TMDB sends no {@code poster_path}.
 */
public record TmdbResult(
        long tmdbId,
        String mediaType,
        String title,
        Integer releaseYear,
        String posterUrl) {
}
