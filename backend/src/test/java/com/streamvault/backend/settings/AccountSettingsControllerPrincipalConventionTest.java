package com.streamvault.backend.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Phase 4 failing test for the gap in Brian's Changes Requested review on PR #25.
 *
 * ADR-001 and CONTRIBUTING.md require Layer 1 controller slice tests to populate the security
 * context with {@code @WithMockUser} or a custom {@code @WithSecurityContext} annotation, not by
 * calling {@code SecurityContextHolder} directly. Because {@code AccountSettingsController} reads
 * a custom principal type ({@code AuthenticatedUser}, not a Spring {@code UserDetails}),
 * {@code @WithMockUser} alone cannot supply it -- the codebase needs a reusable custom
 * {@code @WithSecurityContext} annotation, which is exactly what ADR-001 anticipates with
 * "or a custom {@code @WithSecurityContext} for more complex principal shapes".
 *
 * This test is the reference implementation of that convention and pins the positive half of the
 * contract: {@code @AuthenticationPrincipal AuthenticatedUser} must still resolve to the seeded
 * user, and the resolved {@code userId()} must be the one threaded into the service call.
 *
 * Expected to fail to compile until Dev adds
 * {@code com.streamvault.backend.testsupport.WithMockAuthenticatedUser} (a {@code @WithSecurityContext}
 * meta-annotation) plus its {@code WithSecurityContextFactory}, per
 * docs/specs/design/story-005-brian-review-r1.md. Once that exists and
 * {@code AccountSettingsControllerTest} is refactored to the same annotation, the whole slice
 * suite is back on the agreed convention.
 */
@WebMvcTest(AccountSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class AccountSettingsControllerPrincipalConventionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSettingsService accountSettingsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")
    void should_resolveAuthenticationPrincipalFromWithSecurityContext_when_gettingSettings() throws Exception {
        when(accountSettingsService.getSettings(42L))
                .thenReturn(new AccountSettingsResponse("LOVE_LIKE_MEH_DISLIKE_HATE"));

        mockMvc.perform(get("/api/account/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("LOVE_LIKE_MEH_DISLIKE_HATE"));

        verify(accountSettingsService).getSettings(42L);
    }

    @Test
    @WithMockAuthenticatedUser(userId = 7L, email = "someone-else@example.com")
    void should_threadResolvedPrincipalUserIdIntoService_when_updatingSettings() throws Exception {
        when(accountSettingsService.updateRatingType(eq(7L), any()))
                .thenReturn(new AccountSettingsResponse("THUMBS_UP_THUMBS_DOWN"));

        mockMvc.perform(patch("/api/account/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"THUMBS_UP_THUMBS_DOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("THUMBS_UP_THUMBS_DOWN"));

        verify(accountSettingsService).updateRatingType(eq(7L), any());
    }
}
