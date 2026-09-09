package com.streamvault.backend.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.streamvault.backend.settings.exception.InvalidRatingTypeException;
import com.streamvault.backend.testsupport.WithMockAuthenticatedUser;

/**
 * Contract lives in docs/specs/design/story-005-api-contracts.md.
 *
 * First test in this codebase to exercise {@code @AuthenticationPrincipal} inside a
 * {@code @WebMvcTest} slice. Per ADR-001 the authenticated principal is populated with the custom
 * {@link WithMockAuthenticatedUser} {@code @WithSecurityContext} annotation rather than by touching
 * the security context holder directly, since {@code @WithMockUser} seeds a Spring
 * {@code UserDetails} principal and this controller resolves the codebase's own
 * {@link com.streamvault.backend.auth.AuthenticatedUser} type.
 *
 * {@code addFilters = false} stays consistent with this codebase's other controller slice tests.
 * {@code @WebMvcTest} does not load plain {@code @Configuration} classes by default, so
 * {@link SecurityConfig} (which registers the {@code @AuthenticationPrincipal} argument resolver via
 * {@code @EnableWebSecurity}) is imported explicitly.
 */
@WebMvcTest(AccountSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockAuthenticatedUser(userId = 42L, email = "user@example.com")
class AccountSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSettingsService accountSettingsService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void should_return200WithCurrentRatingType_when_authenticatedUserRequestsSettings() throws Exception {
        when(accountSettingsService.getSettings(42L))
                .thenReturn(new AccountSettingsResponse("LOVE_LIKE_MEH_DISLIKE_HATE"));

        mockMvc.perform(get("/api/account/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("LOVE_LIKE_MEH_DISLIKE_HATE"));
    }

    @Test
    void should_return200WithUpdatedRatingType_when_validRatingTypeIsSubmitted() throws Exception {
        when(accountSettingsService.updateRatingType(eq(42L), any()))
                .thenReturn(new AccountSettingsResponse("THUMBS_UP_THUMBS_DOWN"));

        mockMvc.perform(patch("/api/account/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"THUMBS_UP_THUMBS_DOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("THUMBS_UP_THUMBS_DOWN"));
    }

    @Test
    void should_return400_when_ratingTypeIsBlank() throws Exception {
        mockMvc.perform(patch("/api/account/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.ratingType").value("Rating type is required"));
    }

    @Test
    void should_return400WithClearMessage_when_ratingTypeIsUnsupportedValue() throws Exception {
        when(accountSettingsService.updateRatingType(eq(42L), any()))
                .thenThrow(new InvalidRatingTypeException("FIVE_STARS"));

        mockMvc.perform(patch("/api/account/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"FIVE_STARS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Invalid rating type 'FIVE_STARS'. Valid options are: "
                                + "LOVE_LIKE_MEH_DISLIKE_HATE, THUMBS_UP_THUMBS_DOWN, HALF_STAR_OUT_OF_5."));
    }
}
