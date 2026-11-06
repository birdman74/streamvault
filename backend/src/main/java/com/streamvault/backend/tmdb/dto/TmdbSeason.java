package com.streamvault.backend.tmdb.dto;

import java.util.List;

/**
 * One season within a {@link TmdbSeries}, including season {@code 0} for specials (AC-9), which is
 * stored as its own grouping rather than excluded.
 */
public record TmdbSeason(int seasonNumber, List<TmdbEpisode> episodes) {
}
