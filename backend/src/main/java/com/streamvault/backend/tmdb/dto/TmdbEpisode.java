package com.streamvault.backend.tmdb.dto;

/**
 * One episode within a {@link TmdbSeason}. {@code title} is {@code null} when TMDB omits the
 * episode's {@code name}.
 */
public record TmdbEpisode(int episodeNumber, String title) {
}
