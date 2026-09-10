package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.streamvault.backend.library.dto.LibraryMovieResponse;
import com.streamvault.backend.library.exception.DuplicateLibraryMovieException;
import com.streamvault.backend.library.exception.InvalidWatchStatusException;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. {@code LibraryMovieController},
 * {@code LibraryMovieService}, {@code LibraryMovieResponse}, {@code DuplicateLibraryMovieException},
 * {@code InvalidWatchStatusException}, and {@code TmdbTitleNotFoundException} do not exist yet; this
 * test is expected to fail to compile until Dev implements them.
 *
 * <p>Mirrors {@code AccountSettingsControllerTest} exactly, per ADR-001: {@code addFilters = false},
 * {@code @Import(SecurityConfig.class)} for the {@code @AuthenticationPrincipal} argument resolver,
 * {@link WithMockAuthenticatedUser} to seed the principal. Covers the 201 body (AC-1, AC-2), the
 * principal id and status string threading into the service (AC-3, AC-6), and the 400 / 404 / 409 /
 * 502 mappings (AC-3, AC-7, AC-4). It cannot assert 401 because filters are disabled -- that is
 * {@code LibraryEndpointsSecurityTest}'s job (AC-6, Layer 2).
 */
@WebMvcTest(LibraryMovieController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockAuthenticatedUser(userId = 7L, email = "viewer@example.com")
class LibraryMovieControllerTest {

    private static final String TMDB_UNAVAILABLE_MESSAGE =
            "The movie database is temporarily unavailable. Please try again in a moment.";
    private static final String TMDB_NOT_FOUND_MESSAGE = "We could not find that movie on TMDB.";
    private static final String DUPLICATE_MESSAGE = "This movie is already in your library.";
    private static final String INVALID_STATUS_MESSAGE =
            "Invalid status 'SOON'. Valid options are: PLANNED, CURRENTLY_WATCHING, WATCHED.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryMovieService libraryMovieService;

    @MockitoBean
    private JwtService jwtService;

    private LibraryMovieResponse inceptionResponse(String status) {
        return new LibraryMovieResponse(1L, 27205L, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg", status, Instant.parse("2026-09-10T12:00:00Z"));
    }

    @Test
    void should_return201WithTheCreatedEntry_when_movieIsAdded() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull()))
                .thenReturn(inceptionResponse("PLANNED"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.tmdbId").value(27205))
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.releaseYear").value(2010))
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/inception.jpg"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.addedAt").value("2026-09-10T12:00:00Z"));
    }

    @Test
    void should_threadAuthenticatedUserIdIntoTheService_when_adding() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull()))
                .thenReturn(inceptionResponse("PLANNED"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated());

        verify(libraryMovieService).addMovie(eq(7L), eq(27205L), isNull());
    }

    @Test
    void should_passChosenStatusThroughToTheService_when_statusIsInBody() throws Exception {
        when(libraryMovieService.addMovie(7L, 550L, "WATCHED"))
                .thenReturn(new LibraryMovieResponse(2L, 550L, "Fight Club", 1999, null, "WATCHED",
                        Instant.parse("2026-09-10T12:00:00Z")));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":550,\"status\":\"WATCHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WATCHED"));

        verify(libraryMovieService).addMovie(7L, 550L, "WATCHED");
    }

    @Test
    void should_return400_when_tmdbIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLANNED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.tmdbId").exists());
    }

    @Test
    void should_return400_when_tmdbIdIsNotPositive() throws Exception {
        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.tmdbId").exists());
    }

    @Test
    void should_return400WithClearMessage_when_statusIsUnsupported() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), eq("SOON")))
                .thenThrow(new InvalidWatchStatusException("SOON"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205,\"status\":\"SOON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(INVALID_STATUS_MESSAGE));
    }

    @Test
    void should_return409WithClearMessage_when_movieAlreadyInLibrary() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull()))
                .thenThrow(new DuplicateLibraryMovieException());

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(DUPLICATE_MESSAGE));
    }

    @Test
    void should_return404WithClearMessage_when_tmdbDoesNotRecognizeTheId() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(999999L), isNull()))
                .thenThrow(new TmdbTitleNotFoundException(999999L));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(TMDB_NOT_FOUND_MESSAGE));
    }

    @Test
    void should_return502_when_tmdbIsUnavailable() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull()))
                .thenThrow(new TmdbUnavailableException("upstream 503"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(TMDB_UNAVAILABLE_MESSAGE));
    }

    @Test
    void should_stillServeAValidAdd_afterAPriorTmdbOutage() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(111L), isNull()))
                .thenThrow(new TmdbUnavailableException("boom"));
        when(libraryMovieService.addMovie(eq(7L), eq(222L), isNull()))
                .thenReturn(new LibraryMovieResponse(3L, 222L, "Working", 2020, null, "PLANNED",
                        Instant.parse("2026-09-10T12:00:00Z")));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":111}"))
                .andExpect(status().isBadGateway());

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":222}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Working"));
    }
}
