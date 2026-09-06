package com.streamvault.backend.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.auth.JwtService;
import com.streamvault.backend.config.SecurityConfig;
import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.settings.exception.InvalidRatingTypeException;

/**
 * Contract lives in docs/specs/design/story-005-api-contracts.md. Expected to fail to compile
 * until Dev adds AccountSettingsController/AccountSettingsService per the contract.
 *
 * First test in this codebase to exercise @AuthenticationPrincipal inside a @WebMvcTest slice.
 * With addFilters = false (matching this codebase's other controller slice tests), the real
 * security filter chain never runs, so the security-context-repository-based
 * SecurityMockMvcRequestPostProcessors.authentication(...) postprocessor has nothing to load the
 * saved context back from at dispatch time. Setting SecurityContextHolder directly instead works
 * regardless of addFilters, since MockMvc dispatches synchronously on the test thread and
 * AuthenticationPrincipalArgumentResolver reads the same thread-local SecurityContextHolder.
 * @WebMvcTest does not load plain @Configuration classes by default, so SecurityConfig (which is
 * what registers that argument resolver via @EnableWebSecurity) must be imported explicitly.
 */
@WebMvcTest(AccountSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class AccountSettingsControllerTest {

    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(42L, "user@example.com");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSettingsService accountSettingsService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of()));
    }

    @AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
