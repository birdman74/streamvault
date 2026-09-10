package com.streamvault.backend.tmdb;

import org.springframework.stereotype.Service;

import com.streamvault.backend.tmdb.dto.TmdbResultPage;

/**
 * Owns the request defaults ({@code page} null to 1, {@code list} null to {@code POPULAR}) and then
 * delegates to the {@link TmdbGateway}. Collaborates with nothing else - no repository and no
 * persistence bean of any kind (AC-7). Any
 * {@link com.streamvault.backend.tmdb.exception.TmdbUnavailableException} from the gateway
 * propagates unchanged for {@code GlobalExceptionHandler} to map (AC-6).
 */
@Service
public class TmdbCatalogService {

    private static final int DEFAULT_PAGE = 1;

    private final TmdbGateway tmdbGateway;

    public TmdbCatalogService(TmdbGateway tmdbGateway) {
        this.tmdbGateway = tmdbGateway;
    }

    public TmdbResultPage search(String query, Integer page) {
        return tmdbGateway.search(query, page == null ? DEFAULT_PAGE : page);
    }

    public TmdbResultPage browse(TmdbBrowseList list, Integer page) {
        return tmdbGateway.browse(
                list == null ? TmdbBrowseList.POPULAR : list,
                page == null ? DEFAULT_PAGE : page);
    }
}
