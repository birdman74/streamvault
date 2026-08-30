package com.streamvault.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.streamvault.backend.auth.dto.AuthResponse;
import com.streamvault.backend.auth.exception.GoogleAccountEmailCollisionException;
import com.streamvault.backend.auth.exception.GoogleSignInException;

/**
 * Contract lives in docs/specs/design/story-002-api-contracts.md. Expected to fail to compile
 * until Dev adds the POST /api/auth/google endpoint to AuthController and wires GoogleAuthService.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerGoogleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void should_return200WithToken_when_googleSignInSucceedsForNewUser() throws Exception {
        when(googleAuthService.googleSignIn(any())).thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"a-google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void should_return400_when_idTokenIsBlank() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return401WithClearErrorMessage_when_googleTokenVerificationFails() throws Exception {
        when(googleAuthService.googleSignIn(any())).thenThrow(new GoogleSignInException());

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"an-invalid-or-denied-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Google sign-in failed. Please try again."));
    }

    @Test
    void should_return409WithExactCollisionMessage_when_emailMatchesExistingLocalAccount() throws Exception {
        when(googleAuthService.googleSignIn(any())).thenThrow(new GoogleAccountEmailCollisionException());

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"a-google-id-token\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error")
                        .value("An account with this email already exists. Please sign in with your password."));
    }
}
