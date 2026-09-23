package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.streamvault.backend.tmdb.dto.TmdbEpisode;
import com.streamvault.backend.tmdb.dto.TmdbSeason;
import com.streamvault.backend.tmdb.dto.TmdbSeries;
import com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. The {@code library} package's
 * series support, {@code TmdbGateway#series}, {@code TmdbSeries}/{@code TmdbSeason}/
 * {@code TmdbEpisode}, and {@code TmdbSeriesNotFoundException} do not exist yet; this test is
 * expected to fail to compile until Dev implements them.
 *
 * <p>Layer 2 per ADR-001, mirroring story-007's {@code LibraryEndpointsSecurityTest}. Kept as a
 * separate class rather than added to that file so {@code LibraryEndpointsSecurityTest} stays
 * untouched. Real HTTP through the full security filter chain and a real H2 database. This is the
 * AC-7 auth home (only signed-in users may add; rejected consistently with the rest of the API) and
 * the {@code SecurityConfig} invariant home (adding {@code /api/library/series} must not widen
 * {@code permitAll()} nor move an existing boundary - it already falls under
 * {@code /api/library/**} from story-007). It is also the full-stack home for AC-4 (every episode
 * Planned end-to-end), AC-5 (same user re-add -> 409), AC-6 (two users, same TMDB id, both succeed),
 * and AC-8 (unknown id -> 404, nothing written). {@code TmdbGateway} is mocked so no live TMDB call
 * is made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LibrarySeriesEndpointsSecurityTest {

    private static final long GOT_ID = 1399L;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_series_security_flow;MODE=PostgreSQL");
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
    private LibrarySeriesRepository librarySeriesRepository;

    @MockitoBean
    private TmdbGateway tmdbGateway;

    @BeforeEach
    void resetLibrary() {
        librarySeriesRepository.deleteAll();
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

    private void stubGameOfThrones() {
        when(tmdbGateway.series(GOT_ID)).thenReturn(new TmdbSeries(GOT_ID, "Game of Thrones", 2011,
                "https://image.tmdb.org/t/p/w500/got.jpg",
                List.of(new TmdbSeason(1, List.of(
                        new TmdbEpisode(1, "Winter Is Coming"),
                        new TmdbEpisode(2, "The Kingsroad"))))));
    }

    @Test
    void should_return401_when_addSeriesIsCalledWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401WithTheSameBodyAsTheRestOfTheApi_when_seriesIsCalledUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/library/series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Authentication required\"}"));
    }

    @Test
    void should_return201_when_addSeriesIsCalledWithAValidJwt() throws Exception {
        String token = registerAndLogin("series-add-auth@example.com");
        stubGameOfThrones();

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tmdbId").value(1399))
                .andExpect(jsonPath("$.title").value("Game of Thrones"));
    }

    @Test
    void should_setEveryEpisodeToPlannedEndToEnd_when_seriesIsAdded() throws Exception {
        String token = registerAndLogin("series-planned-status@example.com");
        stubGameOfThrones();

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasons[0].episodes[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.seasons[0].episodes[1].status").value("PLANNED"));
    }

    @Test
    void should_persistAcrossUsersIndependently_when_twoUsersAddTheSameSeries() throws Exception {
        String tokenA = registerAndLogin("series-user-a@example.com");
        String tokenB = registerAndLogin("series-user-b@example.com");
        stubGameOfThrones();

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":1399}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This series is already in your library."));
    }

    @Test
    void should_return404_when_addingATmdbIdTmdbDoesNotRecognize() throws Exception {
        String token = registerAndLogin("series-unknown-id@example.com");
        when(tmdbGateway.series(anyLong())).thenThrow(new TmdbSeriesNotFoundException(9_999_999L));

        mockMvc.perform(post("/api/library/series")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tmdbId\":9999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("We could not find that series on TMDB."));

        assertThat(librarySeriesRepository.count()).isZero();
    }

    @Test
    void should_stillPermitHealthWithoutAuthentication_when_seriesRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void should_stillAuthenticateTheAuthMeEndpoint_when_seriesRoutesAreAdded() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("series-me-unaffected@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("series-me-unaffected@example.com"));
    }
}
