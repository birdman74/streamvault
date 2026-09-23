package com.streamvault.backend.library.dto;

import java.time.Instant;
import java.util.List;

/**
 * The created library entry, carrying enough TMDB catalog data to render without another TMDB call
 * (AC-3), plus the full season/episode tree (AC-2). {@code firstAirYear} and {@code posterUrl} are
 * the only nullable series-level fields - null when TMDB itself does not provide them.
 */
public record LibrarySeriesResponse(
        Long id,
        long tmdbId,
        String title,
        Integer firstAirYear,
        String posterUrl,
        Instant addedAt,
        List<LibrarySeasonResponse> seasons) {
}
