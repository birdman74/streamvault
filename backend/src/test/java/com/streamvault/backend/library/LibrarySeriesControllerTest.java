package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.JwtService;
import com.streamvault.backend.config.SecurityConfig;
import com.streamvault.backend.library.dto.LibraryEpisodeResponse;
import com.streamvault.backend.library.dto.LibrarySeasonResponse;
import com.streamvault.backend.library.dto.LibrarySeriesResponse;
import com.streamvault.backend.library.exception.DuplicateLibrarySeriesException;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;
import com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. {@code LibrarySeriesController},
 * {@code LibrarySeriesService}, {@code LibrarySeriesResponse} and its collaborators,
 * {@code DuplicateLibrarySeriesException}, and {@code TmdbSeriesNotFoundException} do not exist yet;
 * this test is expected to fail to compile until Dev implements them.
 *
 * <p>Mirrors story-007's {@code LibraryMovieControllerTest} exactly, per ADR-001:
 * {@code addFilters = false}, {@code @Import(SecurityConfig.class)} for the
 * {@code @AuthenticationPrincipal} argument resolver, {@link WithMockAuthenticatedUser} to seed the
 * principal. Covers the 201 body including the nested {@code seasons[].episodes[]} shape (AC-1,
 * AC-2, AC-3, AC-4), the principal id threading into the service (AC-7), and the 400 / 404 / 409 /
 * 502 mappings (AC-5, AC-8). It cannot assert 401 because filters are disabled -- that is
 * {@code LibrarySeriesEndpointsSecurityTest}'s job (AC-7, Layer 2).
 */
@WebMvcTest(LibrarySeriesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockAuthenticatedUser(userId = 7L, email = "viewer@example.com")
class LibrarySeriesControllerTest {

    private static final String TMDB_UNAVAILABLE_MESSAGE =
            "The movie database is temporarily unavailable. Please try again in a moment.";
    private static final String TMDB_NOT_FOUND_MESSAGE = "We could not find that series on TMDB.";
    private static final String DUPLICATE_MESSAGE = "This series is already in your library.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibrarySeriesService librarySeriesService;

    @MockitoBean
    private JwtService jwtService;

    private LibrarySeriesResponse gameOfThronesResponse() {
        return new LibrarySeriesResponse(1L, 1399L, "Game of Thrones", 2011,
                "https://image.tmdb.org/t/p/w500/got.jpg", Instant.parse("2026-09-23T12:00:00Z"),
                List.of(
                        new LibrarySeasonResponse(0, List.of(
                                new LibraryEpisodeResponse(1, "Series Recap", "PLANNED"))),
                        new LibrarySeasonResponse(1, List.of(
                                new LibraryEpisodeResponse(1, "Winter Is Coming", "PLANNED"),
                                new LibraryEpisodeResponse(2, "The Kingsroad", "PLANNED")))));
    }

    @Test
    void should_return201WithTheCreatedEntryIncludingSeasonsAndEpisodes_when_seriesIsAdded() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L))).thenReturn(gameOfThronesResponse());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tmdbId").value(1399))
                .andExpect(jsonPath("$.title").value("Game of Thrones"))
                .andExpect(jsonPath("$.firstAirYear").value(2011))
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/got.jpg"))
                .andExpect(jsonPath("$.addedAt").value("2026-09-23T12:00:00Z"))
                .andExpect(jsonPath("$.seasons[0].seasonNumber").value(0))
                .andExpect(jsonPath("$.seasons[0].episodes[0].episodeNumber").value(1))
                .andExpect(jsonPath("$.seasons[0].episodes[0].title").value("Series Recap"))
                .andExpect(jsonPath("$.seasons[0].episodes[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.seasons[1].seasonNumber").value(1))
                .andExpect(jsonPath("$.seasons[1].episodes[0].title").value("Winter Is Coming"))
                .andExpect(jsonPath("$.seasons[1].episodes[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.seasons[1].episodes[1].title").value("The Kingsroad"))
                .andExpect(jsonPath("$.seasons[1].episodes[1].status").value("PLANNED"));
    }

    @Test
    void should_threadAuthenticatedUserIdIntoTheService_when_adding() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L))).thenReturn(gameOfThronesResponse());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated());

        verify(librarySeriesService).addSeries(eq(7L), eq(1399L));
    }

    @Test
    void should_return400_when_tmdbIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.tmdbId").exists());
    }

    @Test
    void should_return400_when_tmdbIdIsNotPositive() throws Exception {
        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.tmdbId").exists());
    }

    @Test
    void should_return409WithClearMessage_when_seriesAlreadyInLibrary() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L)))
                .thenThrow(new DuplicateLibrarySeriesException());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(DUPLICATE_MESSAGE));
    }

    @Test
    void should_return404WithClearMessage_when_tmdbDoesNotRecognizeTheId() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(999999L)))
                .thenThrow(new TmdbSeriesNotFoundException(999999L));

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(TMDB_NOT_FOUND_MESSAGE));
    }

    @Test
    void should_return502_when_tmdbIsUnavailable() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L)))
                .thenThrow(new TmdbUnavailableException("upstream 503"));

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(TMDB_UNAVAILABLE_MESSAGE));
    }
}
