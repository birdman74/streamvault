package com.streamvault.backend.library;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.streamvault.backend.library.dto.LibraryListResponse;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. {@code LibraryController} and its
 * collaborators do not exist yet; this test is expected to fail to compile until Dev implements
 * them.
 *
 * <p>Mirrors {@code LibraryMovieControllerPrincipalConventionTest}. Pins AC-7: the user id passed
 * into {@code LibraryService.list} is always {@code principal.userId()} resolved from the JWT,
 * never anything else, and a different principal yields a different call. {@code LibraryListRequest}
 * has no {@code userId} component, so there is nothing in the request for a client to override in
 * the first place.
 */
@WebMvcTest(LibraryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class LibraryControllerPrincipalConventionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LibraryService libraryService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")
    void should_threadResolvedPrincipalUserIdIntoService_when_listing() throws Exception {
        when(libraryService.list(eq(42L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library")).andExpect(status().isOk());

        verify(libraryService).list(eq(42L), eq(null), eq(null), eq(null), eq(null));
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_useADifferentPrincipalUserId_when_aDifferentUserLists() throws Exception {
        when(libraryService.list(eq(7L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new LibraryListResponse(1, 20, 0, 0, List.of()));

        mockMvc.perform(get("/api/library")).andExpect(status().isOk());

        verify(libraryService).list(eq(7L), eq(null), eq(null), eq(null), eq(null));
        verify(libraryService, org.mockito.Mockito.never())
                .list(eq(42L), eq(null), eq(null), eq(null), eq(null));
    }
}
