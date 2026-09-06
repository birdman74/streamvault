package com.streamvault.backend.settings;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.AuthenticatedUser;
import com.streamvault.backend.settings.dto.AccountSettingsResponse;
import com.streamvault.backend.settings.exception.InvalidRatingTypeException;

/**
 * Contract lives in docs/specs/design/story-005-api-contracts.md. Expected to fail to compile
 * until Dev adds AccountSettingsController/AccountSettingsService per the contract.
 *
 * First test in this codebase to exercise @AuthenticationPrincipal inside a @WebMvcTest slice: the
 * `authentication(...)` request post-processor from spring-security-test populates the
 * SecurityContext for the duration of a single request independent of whether the real filter
 * chain is applied, so it works the same with addFilters = false as the rest of this codebase's
 * controller slice tests use.
 */
@WebMvcTest(AccountSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AccountSettingsControllerTest {

    private static final AuthenticatedUser PRINCIPAL = new AuthenticatedUser(42L, "user@example.com");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSettingsService accountSettingsService;

    private static Authentication asPrincipal() {
        return new UsernamePasswordAuthenticationToken(PRINCIPAL, null, List.of());
    }

    @Test
    void should_return200WithCurrentRatingType_when_authenticatedUserRequestsSettings() throws Exception {
        when(accountSettingsService.getSettings(42L))
                .thenReturn(new AccountSettingsResponse("LOVE_LIKE_MEH_DISLIKE_HATE"));

        mockMvc.perform(get("/api/account/settings").with(authentication(asPrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("LOVE_LIKE_MEH_DISLIKE_HATE"));
    }

    @Test
    void should_return200WithUpdatedRatingType_when_validRatingTypeIsSubmitted() throws Exception {
        when(accountSettingsService.updateRatingType(eq(42L), any()))
                .thenReturn(new AccountSettingsResponse("THUMBS_UP_THUMBS_DOWN"));

        mockMvc.perform(patch("/api/account/settings")
                        .with(authentication(asPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"THUMBS_UP_THUMBS_DOWN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("THUMBS_UP_THUMBS_DOWN"));
    }

    @Test
    void should_return400_when_ratingTypeIsBlank() throws Exception {
        mockMvc.perform(patch("/api/account/settings")
                        .with(authentication(asPrincipal()))
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
                        .with(authentication(asPrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingType\":\"FIVE_STARS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Invalid rating type 'FIVE_STARS'. Valid options are: "
                                + "LOVE_LIKE_MEH_DISLIKE_HATE, THUMBS_UP_THUMBS_DOWN, HALF_STAR_OUT_OF_5."));
    }
}
