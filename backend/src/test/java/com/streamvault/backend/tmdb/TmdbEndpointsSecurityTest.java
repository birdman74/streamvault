package com.streamvault.backend.tmdb;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;

/**
 * Contract lives in docs/specs/design/story-006-api-contracts.md. {@code TmdbGateway},
 * {@code TmdbBrowseList}, and {@code TmdbResultPage} do not exist yet; this test is expected to
 * fail to compile until Dev implements the TMDB feature.
 *
 * <p>Layer 2 per ADR-001, mirroring {@code SecurityConfigAuthFlowTest}: runs real HTTP through the
 * full security filter chain. This is the AC-8 home (only signed-in users may search or browse,
 * rejected consistently with the rest of the API) and the {@code SecurityConfig} invariant home
 * (adding {@code /api/tmdb/**} must not widen {@code permitAll()} nor move any existing boundary).
 * {@code TmdbGateway} is mocked so no live TMDB call is made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TmdbEndpointsSecurityTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tmdb_security_flow;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("tmdb.api-key", () -> "test-tmdb-key");
        registry.add("tmdb.base-url", () -> "https://api.themoviedb.org/3");
        registry.add("tmdb.image-base-url", () -> "https://image.tmdb.org/t/p/w500");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TmdbGateway tmdbGateway;

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
    void should_return401_when_searchIsCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/tmdb/search").param("query", "inception"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401_when_browseIsCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/tmdb/browse"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401WithTheSameBodyAsTheRestOfTheApi_when_tmdbIsCalledUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tmdb/search").param("query", "inception"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Authentication required\"}"));
    }

    @Test
    void should_return200_when_searchIsCalledWithAValidJwt() throws Exception {
        String token = registerAndLogin("tmdb-search-auth@example.com");
        when(tmdbGateway.search("inception", 1)).thenReturn(new TmdbResultPage(1, 0, 0, List.of()));

        mockMvc.perform(get("/api/tmdb/search").param("query", "inception")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void should_return200_when_browseIsCalledWithAValidJwt() throws Exception {
        String token = registerAndLogin("tmdb-browse-auth@example.com");
        when(tmdbGateway.browse(TmdbBrowseList.POPULAR, 1)).thenReturn(new TmdbResultPage(1, 0, 0, List.of()));

        mockMvc.perform(get("/api/tmdb/browse")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void should_stillPermitHealthWithoutAuthentication_when_tmdbRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void should_stillAuthenticateTheAuthMeEndpoint_when_tmdbRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("tmdb-me-unaffected@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("tmdb-me-unaffected@example.com"));
    }
}
