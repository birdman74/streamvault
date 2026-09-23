package com.streamvault.backend.tmdb;

import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.dto.TmdbSeries;

/**
 * The seam that owns the HTTP conversation with TMDB and the TMDB-JSON to {@link TmdbResultPage}
 * mapping, mirroring the {@code GoogleTokenVerifier} / {@code GoogleTokenInfoVerifier} split from
 * story-002. Callers above this interface never see a TMDB wire type. {@link #search} and
 * {@link #browse} throw
 * {@link com.streamvault.backend.tmdb.exception.TmdbUnavailableException} on any upstream failure.
 */
public interface TmdbGateway {

    TmdbResultPage search(String query, int page);

    TmdbResultPage browse(TmdbBrowseList list, int page);

    /**
     * Fetches a single movie by its TMDB id via {@code GET /movie/{tmdbId}}. Throws
     * {@link com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException} on a TMDB
     * {@code 404} (definite "no such movie"), and
     * {@link com.streamvault.backend.tmdb.exception.TmdbUnavailableException} on any other
     * {@code RestClientException} / non-2xx (outage, transport failure, 429, bad-key 401).
     */
    TmdbMovie movie(long tmdbId);

    /**
     * Fetches a single series and its full season/episode structure by its TMDB id, via
     * {@code GET /tv/{tmdbId}} for series-level data followed by one or more batched
     * {@code GET /tv/{tmdbId}?append_to_response=season/{n1},season/{n2},...} calls (up to 20 season
     * numbers per call) for episode detail. Throws
     * {@link com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException} on a TMDB
     * {@code 404} for the series call (definite "no such series"), and
     * {@link com.streamvault.backend.tmdb.exception.TmdbUnavailableException} on any other
     * {@code RestClientException} / non-2xx from the series call or any batched season call.
     */
    TmdbSeries series(long tmdbId);
}
