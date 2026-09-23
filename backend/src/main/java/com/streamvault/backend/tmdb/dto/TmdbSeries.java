package com.streamvault.backend.tmdb.dto;

import java.util.List;

/**
 * The full-tree projection returned by {@code TmdbGateway#series(long)}. {@code firstAirYear} /
 * {@code posterUrl} follow the exact null rules of {@code TmdbMovie}'s equivalent fields.
 */
public record TmdbSeries(long tmdbId, String title, Integer firstAirYear, String posterUrl,
        List<TmdbSeason> seasons) {
}
