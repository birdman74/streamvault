package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.streamvault.backend.library.dto.LibrarySeriesResponse;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. {@code LibrarySeriesController}
 * and its collaborators do not exist yet; this test is expected to fail to compile until Dev
 * implements them.
 *
 * <p>Mirrors story-007's {@code LibraryMovieControllerPrincipalConventionTest}. Pins AC-7: the
 * owning user id is always {@code principal.userId()} resolved from the JWT, and is never read from
 * the request body. {@code AddSeriesRequest} has no {@code userId} component, so a client that sends
 * one must be ignored.
 */
@WebMvcTest(LibrarySeriesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LibrarySeriesControllerPrincipalConventionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibrarySeriesService librarySeriesService;

    @MockitoBean
    private JwtService jwtService;

    private LibrarySeriesResponse response() {
        return new LibrarySeriesResponse(1L, 1399L, "Game of Thrones", 2011, null,
                Instant.parse("2026-09-23T12:00:00Z"), List.of());
    }

    @Test
    @WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")
    void should_threadResolvedPrincipalUserIdIntoService_when_addingASeries() throws Exception {
        when(librarySeriesService.addSeries(eq(42L), eq(1399L))).thenReturn(response());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated());

        verify(librarySeriesService).addSeries(eq(42L), eq(1399L));
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_useADifferentPrincipalUserId_when_aDifferentUserAdds() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L))).thenReturn(response());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated());

        verify(librarySeriesService).addSeries(eq(7L), eq(1399L));
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_ignoreAnyUserIdInTheRequestBody_when_adding() throws Exception {
        when(librarySeriesService.addSeries(eq(7L), eq(1399L))).thenReturn(response());

        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399,\"userId\":999}"))
                .andExpect(status().isCreated());

        verify(librarySeriesService).addSeries(eq(7L), eq(1399L));
        verify(librarySeriesService, org.mockito.Mockito.never()).addSeries(eq(999L), any());
    }
}
