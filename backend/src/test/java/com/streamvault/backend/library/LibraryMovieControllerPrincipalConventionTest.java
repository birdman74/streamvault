package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. {@code LibraryMovieController} and
 * its collaborators do not exist yet; this test is expected to fail to compile until Dev implements
 * them.
 *
 * <p>Mirrors {@code AccountSettingsControllerPrincipalConventionTest}. Pins AC-6: the owning user id
 * is always {@code principal.userId()} resolved from the JWT, and is never read from the request
 * body. {@code AddMovieRequest} has no {@code userId} component, so a client that sends one must be
 * ignored.
 */
@WebMvcTest(LibraryMovieController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LibraryMovieControllerPrincipalConventionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryMovieService libraryMovieService;

    @MockitoBean
    private JwtService jwtService;

    private LibraryMovieResponse response(String status) {
        return new LibraryMovieResponse(1L, 27205L, "Inception", 2010, null, status,
                Instant.parse("2026-09-10T12:00:00Z"));
    }

    @Test
    @WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")
    void should_threadResolvedPrincipalUserIdIntoService_when_addingAMovie() throws Exception {
        when(libraryMovieService.addMovie(eq(42L), eq(27205L), isNull())).thenReturn(response("PLANNED"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated());

        verify(libraryMovieService).addMovie(eq(42L), eq(27205L), isNull());
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_useADifferentPrincipalUserId_when_aDifferentUserAdds() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull())).thenReturn(response("PLANNED"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated());

        verify(libraryMovieService).addMovie(eq(7L), eq(27205L), isNull());
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_ignoreAnyUserIdInTheRequestBody_when_adding() throws Exception {
        when(libraryMovieService.addMovie(eq(7L), eq(27205L), isNull())).thenReturn(response("PLANNED"));

        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205,\"userId\":999}"))
                .andExpect(status().isCreated());

        verify(libraryMovieService).addMovie(eq(7L), eq(27205L), isNull());
        verify(libraryMovieService, org.mockito.Mockito.never()).addMovie(eq(999L), any(), any());
    }
}
