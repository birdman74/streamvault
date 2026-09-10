package com.streamvault.backend.tmdb.dto;

/**
 * The single-movie projection returned by {@code TmdbGateway.movie(long)} for {@code GET /movie/{id}}.
 * {@code releaseYear} and {@code posterUrl} follow the exact null rules of story-006's
 * {@code TmdbResult}: {@code releaseYear} is {@code null} when TMDB omits / empties / sends an
 * unparseable {@code release_date}, {@code posterUrl} is {@code null} when TMDB sends no
 * {@code poster_path}.
 */
public record TmdbMovie(
        long tmdbId,
        String title,
        Integer releaseYear,
        String posterUrl) {
}
