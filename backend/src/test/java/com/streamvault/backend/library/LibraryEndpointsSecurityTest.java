package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. The {@code library} package,
 * {@code TmdbGateway#movie}, {@code TmdbMovie}, and {@code TmdbTitleNotFoundException} do not exist
 * yet; this test is expected to fail to compile until Dev implements them.
 *
 * <p>Layer 2 per ADR-001, mirroring {@code TmdbEndpointsSecurityTest}: real HTTP through the full
 * security filter chain and a real H2 database. This is the AC-6 auth home (only signed-in users may
 * add; rejected consistently with the rest of the API) and the {@code SecurityConfig} invariant home
 * (adding {@code /api/library/**} must not widen {@code permitAll()} nor move an existing boundary).
 * It is also the full-stack home for AC-3 (default and chosen status persist), AC-4 (same user
 * re-add -> 409), AC-5 (two users, same TMDB id, both succeed), and AC-7 (unknown id -> 404, nothing
 * written). {@code TmdbGateway} is mocked so no live TMDB call is made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LibraryEndpointsSecurityTest {

    private static final long INCEPTION_ID = 27205L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_security_flow;MODE=PostgreSQL");
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

    @Autowired
    private LibraryMovieRepository libraryMovieRepository;

    @MockitoBean
    private TmdbGateway tmdbGateway;

    @BeforeEach
    void resetLibrary() {
        libraryMovieRepository.deleteAll();
    }

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

    private void stubInception() {
        when(tmdbGateway.movie(INCEPTION_ID)).thenReturn(
                new TmdbMovie(INCEPTION_ID, "Inception", 2010,
                        "https://image.tmdb.org/t/p/w500/inception.jpg"));
    }

    @Test
    void should_return401_when_addMovieIsCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401WithTheSameBodyAsTheRestOfTheApi_when_libraryIsCalledUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/library/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Authentication required\"}"));
    }

    @Test
    void should_return201_when_addMovieIsCalledWithAValidJwt() throws Exception {
        String token = registerAndLogin("library-add-auth@example.com");
        stubInception();

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tmdbId").value(27205))
                .andExpect(jsonPath("$.title").value("Inception"))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void should_defaultToPlanned_when_statusIsOmittedEndToEnd() throws Exception {
        String token = registerAndLogin("library-default-status@example.com");
        stubInception();

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void should_storeChosenStatusEndToEnd_when_statusProvided() throws Exception {
        String token = registerAndLogin("library-chosen-status@example.com");
        stubInception();

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205,\"status\":\"CURRENTLY_WATCHING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CURRENTLY_WATCHING"));
    }

    @Test
    void should_persistAcrossUsersIndependently_when_twoUsersAddTheSameMovie() throws Exception {
        String tokenA = registerAndLogin("library-user-a@example.com");
        String tokenB = registerAndLogin("library-user-b@example.com");
        stubInception();

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205,\"status\":\"PLANNED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205,\"status\":\"WATCHED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WATCHED"));

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":27205}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This movie is already in your library."));
    }

    @Test
    void should_return404_when_addingATmdbIdTmdbDoesNotRecognize() throws Exception {
        String token = registerAndLogin("library-unknown-id@example.com");
        when(tmdbGateway.movie(anyLong())).thenThrow(new TmdbTitleNotFoundException(9_999_999L));

        mockMvc.perform(post("/api/library/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":9999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("We could not find that movie on TMDB."));

        assertThat(libraryMovieRepository.count()).isZero();
    }

    @Test
    void should_stillPermitHealthWithoutAuthentication_when_libraryRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void should_stillAuthenticateTheAuthMeEndpoint_when_libraryRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("library-me-unaffected@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("library-me-unaffected@example.com"));
    }
}
