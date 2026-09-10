package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.streamvault.backend.tmdb.dto.TmdbResult;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-006-api-contracts.md. {@code TmdbCatalogService},
 * {@code TmdbGateway}, {@code TmdbBrowseList}, {@code TmdbResult}, {@code TmdbResultPage}, and
 * {@code TmdbUnavailableException} do not exist yet; this test is expected to fail to compile until
 * Dev implements them.
 *
 * <p>Mirrors {@code AuthServiceTest} / {@code AccountSettingsServiceTest}: Mockito, mocks the
 * gateway seam. Covers the defaults the service owns (page -> 1, list -> POPULAR), pass-through of
 * results and of the empty page (AC-5), exception propagation (AC-6), and that the service
 * collaborates with nothing but the gateway (AC-7).
 */
@ExtendWith(MockitoExtension.class)
class TmdbCatalogServiceTest {

    @Mock
    private TmdbGateway tmdbGateway;

    private TmdbCatalogService tmdbCatalogService;

    private static final TmdbResultPage EMPTY_PAGE = new TmdbResultPage(1, 0, 0, List.of());

    @BeforeEach
    void setUp() {
        tmdbCatalogService = new TmdbCatalogService(tmdbGateway);
    }

    @Test
    void should_returnResultsFromTheGateway_when_searchIsPerformed() {
        TmdbResultPage gatewayPage = new TmdbResultPage(1, 3, 47, List.of(
                new TmdbResult(27205L, "MOVIE", "Inception", 2010,
                        "https://image.tmdb.org/t/p/w500/inception.jpg"),
                new TmdbResult(2316L, "SERIES", "The Office", 2005, null)));
        when(tmdbGateway.search("inception", 2)).thenReturn(gatewayPage);

        TmdbResultPage result = tmdbCatalogService.search("inception", 2);

        assertThat(result).isEqualTo(gatewayPage);
        assertThat(result.results()).hasSize(2);
    }

    @Test
    void should_defaultToPageOne_when_noPageIsSpecified() {
        when(tmdbGateway.search("inception", 1)).thenReturn(EMPTY_PAGE);

        tmdbCatalogService.search("inception", null);

        verify(tmdbGateway).search("inception", 1);
    }

    @Test
    void should_passTheRequestedPageThrough_when_aPageIsSpecified() {
        when(tmdbGateway.search("inception", 4)).thenReturn(EMPTY_PAGE);

        tmdbCatalogService.search("inception", 4);

        verify(tmdbGateway).search("inception", 4);
    }

    @Test
    void should_returnEmptyPageWithoutError_when_gatewayReturnsNoResults() {
        when(tmdbGateway.search("nomatchwhatsoever", 1)).thenReturn(EMPTY_PAGE);

        TmdbResultPage result = tmdbCatalogService.search("nomatchwhatsoever", null);

        assertThat(result.results()).isEmpty();
        assertThat(result.totalResults()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void should_propagateTmdbUnavailableException_when_gatewayFails() {
        when(tmdbGateway.search("inception", 1))
                .thenThrow(new TmdbUnavailableException("upstream 503"));

        assertThatThrownBy(() -> tmdbCatalogService.search("inception", 1))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_requestPopularList_when_browseListIsNull() {
        when(tmdbGateway.browse(TmdbBrowseList.POPULAR, 1)).thenReturn(EMPTY_PAGE);

        tmdbCatalogService.browse(null, null);

        verify(tmdbGateway).browse(TmdbBrowseList.POPULAR, 1);
    }

    @Test
    void should_requestTheGivenListAndPage_when_browsingTrending() {
        when(tmdbGateway.browse(TmdbBrowseList.TRENDING, 3)).thenReturn(EMPTY_PAGE);

        tmdbCatalogService.browse(TmdbBrowseList.TRENDING, 3);

        verify(tmdbGateway).browse(TmdbBrowseList.TRENDING, 3);
    }

    @Test
    void should_propagateTmdbUnavailableException_when_gatewayFailsDuringBrowse() {
        when(tmdbGateway.browse(TmdbBrowseList.POPULAR, 1))
                .thenThrow(new TmdbUnavailableException("upstream timeout"));

        assertThatThrownBy(() -> tmdbCatalogService.browse(null, null))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_touchNothingButTheGateway_when_searchAndBrowseAreInvoked() {
        when(tmdbGateway.search("x", 1)).thenReturn(EMPTY_PAGE);
        when(tmdbGateway.browse(TmdbBrowseList.POPULAR, 1)).thenReturn(EMPTY_PAGE);

        tmdbCatalogService.search("x", null);
        tmdbCatalogService.browse(null, null);

        verify(tmdbGateway).search("x", 1);
        verify(tmdbGateway).browse(TmdbBrowseList.POPULAR, 1);
        verifyNoMoreInteractions(tmdbGateway);
    }
}
