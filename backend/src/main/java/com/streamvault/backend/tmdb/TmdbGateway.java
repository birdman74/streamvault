package com.streamvault.backend.tmdb;

import com.streamvault.backend.tmdb.dto.TmdbResultPage;

/**
 * The seam that owns the HTTP conversation with TMDB and the TMDB-JSON to {@link TmdbResultPage}
 * mapping, mirroring the {@code GoogleTokenVerifier} / {@code GoogleTokenInfoVerifier} split from
 * story-002. Callers above this interface never see a TMDB wire type. Both methods throw
 * {@link com.streamvault.backend.tmdb.exception.TmdbUnavailableException} on any upstream failure.
 */
public interface TmdbGateway {

    TmdbResultPage search(String query, int page);

    TmdbResultPage browse(TmdbBrowseList list, int page);
}
