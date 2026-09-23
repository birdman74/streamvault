package com.streamvault.backend.library.dto;

/**
 * One episode within a {@link LibrarySeasonResponse}. {@code status} is always {@code "PLANNED"} on
 * add (AC-4). {@code title} is {@code null} when TMDB omits it (AC-2).
 */
public record LibraryEpisodeResponse(int episodeNumber, String title, String status) {
}
