package com.streamvault.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * story-005's implementation added @EnableWebSecurity to SecurityConfig (not part of the agreed
 * design, which stated "no SecurityConfig changes"), justified in the PR as fixing
 * @AuthenticationPrincipal resolution app-wide. No existing test in this codebase ran HTTP
 * requests through the real security filter chain end-to-end (AuthControllerGoogleTest and
 * AccountSettingsControllerTest both use addFilters = false), so this pre-existing, untested
 * behavior change was otherwise unverified. This test exercises the real filter chain to confirm
 * the fix works and that no permitAll/authenticated boundary regressed for the pre-existing
 * STORY-001 /api/auth/me endpoint and the new story-005 endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class SecurityConfigAuthFlowTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:security_config_auth_flow;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void should_return200WithPrincipalEmail_when_meIsCalledWithValidJwtThroughRealFilterChain() throws Exception {
        String token = registerAndLogin("real-chain-me@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("real-chain-me@example.com"));
    }

    @Test
    void should_return401_when_meIsCalledWithNoToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return200WithDefaultRatingType_when_accountSettingsIsCalledWithValidJwtThroughRealFilterChain()
            throws Exception {
        String token = registerAndLogin("real-chain-settings@example.com");

        mockMvc.perform(get("/api/account/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratingType").value("LOVE_LIKE_MEH_DISLIKE_HATE"));
    }

    @Test
    void should_return401_when_accountSettingsIsCalledWithNoToken() throws Exception {
        mockMvc.perform(get("/api/account/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_permitAccessWithoutAuthentication_when_healthEndpointIsCalled() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void should_permitAccessWithoutAuthentication_when_registerEndpointIsCalled() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"permit-all-register@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isCreated());
    }
}
