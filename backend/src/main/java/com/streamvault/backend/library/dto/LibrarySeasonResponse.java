package com.streamvault.backend.library.dto;

import java.util.List;

/**
 * One season within a {@link LibrarySeriesResponse}, in TMDB's listed order, including season
 * {@code 0} if present (AC-9).
 */
public record LibrarySeasonResponse(int seasonNumber, List<LibraryEpisodeResponse> episodes) {
}
