package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.JwtService;
import com.streamvault.backend.config.SecurityConfig;
import com.streamvault.backend.tmdb.dto.TmdbResult;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-006-api-contracts.md. {@code TmdbController},
 * {@code TmdbCatalogService}, {@code TmdbBrowseList}, {@code TmdbResult}, {@code TmdbResultPage},
 * and {@code TmdbUnavailableException} do not exist yet; this test is expected to fail to compile
 * until Dev implements them.
 *
 * <p>Mirrors {@code AccountSettingsControllerTest} exactly, per ADR-001: {@code addFilters = false},
 * {@code @Import(SecurityConfig.class)} for the {@code @AuthenticationPrincipal} argument resolver,
 * and {@link WithMockAuthenticatedUser} to seed the principal. It covers request binding, the
 * validation status codes, the response envelope, page pass-through, the empty result (AC-5), and
 * the 502 mapping (AC-6). It cannot assert 401 because filters are disabled -- that is
 * {@code TmdbEndpointsSecurityTest}'s job (AC-8, Layer 2).
 */
@WebMvcTest(TmdbController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockAuthenticatedUser(userId = 7L, email = "viewer@example.com")
class TmdbControllerTest {

    private static final String TMDB_UNAVAILABLE_MESSAGE =
            "The movie database is temporarily unavailable. Please try again in a moment.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TmdbCatalogService tmdbCatalogService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void should_return200WithMixedMovieAndSeriesResults_when_authenticatedUserSearchesWithAQuery() throws Exception {
        when(tmdbCatalogService.search("inception", null)).thenReturn(new TmdbResultPage(1, 1, 2, List.of(
                new TmdbResult(27205L, "MOVIE", "Inception", 2010,
                        "https://image.tmdb.org/t/p/w500/i.jpg"),
                new TmdbResult(1399L, "SERIES", "Inception: The Cobol Job", 2011, null))));

        mockMvc.perform(get("/api/tmdb/search").param("query", "inception"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].mediaType").value("MOVIE"))
                .andExpect(jsonPath("$.results[1].mediaType").value("SERIES"));
    }

    @Test
    void should_includeTitleMediaTypeYearAndPoster_when_resultsAreReturned() throws Exception {
        when(tmdbCatalogService.search("dune", null)).thenReturn(new TmdbResultPage(1, 1, 2, List.of(
                new TmdbResult(438631L, "MOVIE", "Dune", 2021,
                        "https://image.tmdb.org/t/p/w500/dune.jpg"),
                new TmdbResult(9999L, "MOVIE", "Dune (no poster, no year)", null, null))));

        mockMvc.perform(get("/api/tmdb/search").param("query", "dune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].tmdbId").value(438631))
                .andExpect(jsonPath("$.results[0].title").value("Dune"))
                .andExpect(jsonPath("$.results[0].releaseYear").value(2021))
                .andExpect(jsonPath("$.results[0].posterUrl")
                        .value("https://image.tmdb.org/t/p/w500/dune.jpg"))
                .andExpect(jsonPath("$.results[1].releaseYear").value(nullValue()))
                .andExpect(jsonPath("$.results[1].posterUrl").value(nullValue()));
    }

    @Test
    void should_return200WithBoundedPageEnvelope_when_searchResultsArePaginated() throws Exception {
        when(tmdbCatalogService.search("marvel", null)).thenReturn(new TmdbResultPage(1, 12, 231, List.of(
                new TmdbResult(1L, "MOVIE", "A Marvel Movie", 2019, null))));

        mockMvc.perform(get("/api/tmdb/search").param("query", "marvel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(12))
                .andExpect(jsonPath("$.totalResults").value(231))
                .andExpect(jsonPath("$.results.length()").value(1));
    }

    @Test
    void should_passRequestedPageThroughToTheService_when_pageParamIsProvided() throws Exception {
        when(tmdbCatalogService.search("marvel", 3)).thenReturn(new TmdbResultPage(3, 12, 231, List.of()));

        mockMvc.perform(get("/api/tmdb/search").param("query", "marvel").param("page", "3"))
                .andExpect(status().isOk());

        verify(tmdbCatalogService).search("marvel", 3);
    }

    @Test
    void should_return200WithEmptyResults_when_queryMatchesNothing() throws Exception {
        when(tmdbCatalogService.search("zzznothingmatches", null))
                .thenReturn(new TmdbResultPage(1, 0, 0, List.of()));

        mockMvc.perform(get("/api/tmdb/search").param("query", "zzznothingmatches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0))
                .andExpect(jsonPath("$.totalResults").value(0));
    }

    @Test
    void should_return400_when_searchQueryIsBlank() throws Exception {
        mockMvc.perform(get("/api/tmdb/search").param("query", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.query").value("Search query is required"));
    }

    @Test
    void should_return400_when_searchQueryIsMissing() throws Exception {
        mockMvc.perform(get("/api/tmdb/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.query").exists());
    }

    @Test
    void should_return400_when_pageParamIsLessThanOne() throws Exception {
        mockMvc.perform(get("/api/tmdb/search").param("query", "inception").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.page").exists());
    }

    @Test
    void should_return400_when_pageParamExceedsTmdbMaximum() throws Exception {
        mockMvc.perform(get("/api/tmdb/search").param("query", "inception").param("page", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.page").exists());
    }

    @Test
    void should_return502WithUserFacingMessage_when_tmdbIsUnavailableDuringSearch() throws Exception {
        when(tmdbCatalogService.search("inception", null))
                .thenThrow(new TmdbUnavailableException("upstream 503"));

        mockMvc.perform(get("/api/tmdb/search").param("query", "inception"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(TMDB_UNAVAILABLE_MESSAGE));
    }

    @Test
    void should_stillServeAValidRequest_afterAPriorRequestHitATmdbOutage() throws Exception {
        when(tmdbCatalogService.search("broken", null))
                .thenThrow(new TmdbUnavailableException("boom"));
        when(tmdbCatalogService.search("working", null)).thenReturn(new TmdbResultPage(1, 1, 1, List.of(
                new TmdbResult(1L, "MOVIE", "Working", 2020, null))));

        mockMvc.perform(get("/api/tmdb/search").param("query", "broken"))
                .andExpect(status().isBadGateway());

        mockMvc.perform(get("/api/tmdb/search").param("query", "working"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].title").value("Working"));
    }

    @Test
    void should_return200WithResults_when_authenticatedUserBrowsesWithoutAQuery() throws Exception {
        when(tmdbCatalogService.browse(null, null)).thenReturn(new TmdbResultPage(1, 500, 10000, List.of(
                new TmdbResult(1184918L, "MOVIE", "The Wild Robot", 2024, null))));

        mockMvc.perform(get("/api/tmdb/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.results[0].title").value("The Wild Robot"));
    }

    @Test
    void should_forwardOmittedListAsNullToTheService_when_browsing() throws Exception {
        when(tmdbCatalogService.browse(null, null)).thenReturn(new TmdbResultPage(1, 0, 0, List.of()));

        mockMvc.perform(get("/api/tmdb/browse"))
                .andExpect(status().isOk());

        verify(tmdbCatalogService).browse(null, null);
    }

    @Test
    void should_browseTrendingList_when_listParamIsTrending() throws Exception {
        when(tmdbCatalogService.browse(TmdbBrowseList.TRENDING, null))
                .thenReturn(new TmdbResultPage(1, 1, 1, List.of(
                        new TmdbResult(1L, "SERIES", "A Trending Series", 2023, null))));

        mockMvc.perform(get("/api/tmdb/browse").param("list", "TRENDING"))
                .andExpect(status().isOk());

        verify(tmdbCatalogService).browse(TmdbBrowseList.TRENDING, null);
    }

    @Test
    void should_return400_when_browseListIsNotARecognizedValue() throws Exception {
        mockMvc.perform(get("/api/tmdb/browse").param("list", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.list").exists());
    }

    @Test
    void should_return502WithUserFacingMessage_when_tmdbIsUnavailableDuringBrowse() throws Exception {
        when(tmdbCatalogService.browse(null, null))
                .thenThrow(new TmdbUnavailableException("upstream timeout"));

        mockMvc.perform(get("/api/tmdb/browse"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(TMDB_UNAVAILABLE_MESSAGE));
    }

    @Test
    void should_returnSameResultShapeForBrowseAsForSearch_when_bothAreCalled() throws Exception {
        TmdbResultPage shared = new TmdbResultPage(1, 1, 1, List.of(
                new TmdbResult(42L, "SERIES", "Shape Test", 2019,
                        "https://image.tmdb.org/t/p/w500/s.jpg")));
        when(tmdbCatalogService.search("shape", null)).thenReturn(shared);
        when(tmdbCatalogService.browse(null, null)).thenReturn(shared);

        String searchBody = mockMvc.perform(get("/api/tmdb/search").param("query", "shape"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String browseBody = mockMvc.perform(get("/api/tmdb/browse"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(searchBody).isEqualTo(browseBody);
    }
}
