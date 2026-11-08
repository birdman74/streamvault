package com.streamvault.backend.library;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. The {@code LibraryController} /
 * {@code LibraryService} pair, plus the new {@code findByUserId} finders, do not exist yet; this
 * test is expected to fail to compile until Dev implements them.
 *
 * <p>Layer 2 per ADR-001, mirroring {@code LibraryEndpointsSecurityTest}: real HTTP through the
 * full security filter chain and a real H2 database, no mocks for the repositories. This is the
 * AC-7 auth home (only signed-in users may list; a request without a valid JWT is rejected
 * consistently with the rest of the API) and the two-user isolation home (AC-7's "own library
 * only"). It is also the full-stack home for AC-1 (movies and series merged), AC-3/AC-4/AC-5 (type
 * and status filters, combined), AC-6 (pagination), and AC-8 (empty library, and a filter
 * combination matching nothing). {@code SecurityConfig}'s invariant - adding {@code /api/library}
 * must not widen {@code permitAll()} nor move an existing boundary - is checked the same way
 * {@code LibraryEndpointsSecurityTest} already checks it for {@code /api/library/movies}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LibraryListEndpointsSecurityTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_list_security_flow;MODE=PostgreSQL");
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

    @Autowired
    private LibrarySeriesRepository librarySeriesRepository;

    @BeforeEach
    void resetLibrary() {
        libraryMovieRepository.deleteAll();
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

    private LibraryMovie seedMovie(Long userId, long tmdbId, String title, WatchStatus status) {
        return libraryMovieRepository.save(new LibraryMovie(userId, tmdbId, title, 2000, null, status));
    }

    private LibrarySeries seedSeries(Long userId, long tmdbId, String title, WatchStatus episodeStatus) {
        LibrarySeries series = new LibrarySeries(userId, tmdbId, title, 2000, null);
        LibrarySeason season = new LibrarySeason(1);
        season.addEpisode(new LibraryEpisode(1, "Pilot", episodeStatus));
        series.addSeason(season);
        return librarySeriesRepository.save(series);
    }

    @Test
    void should_return401_when_libraryIsListedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/library"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_return401WithTheSameBodyAsTheRestOfTheApi_when_libraryIsListedUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/library"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Authentication required\"}"));
    }

    @Test
    void should_return200WithBothMoviesAndSeriesMerged_when_aSignedInUserListsTheirLibrary() throws Exception {
        String token = registerAndLogin("library-list-merge@example.com");
        Long userId = currentUserId(token, "library-list-merge@example.com");
        seedMovie(userId, 27205L, "Inception", WatchStatus.WATCHED);
        seedSeries(userId, 1399L, "Game of Thrones", WatchStatus.PLANNED);

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void should_returnOnlyMovies_when_typeFilterIsMovieOnlyEndToEnd() throws Exception {
        String token = registerAndLogin("library-list-type-filter@example.com");
        Long userId = currentUserId(token, "library-list-type-filter@example.com");
        seedMovie(userId, 27205L, "Inception", WatchStatus.WATCHED);
        seedSeries(userId, 1399L, "Game of Thrones", WatchStatus.PLANNED);

        mockMvc.perform(get("/api/library?type=MOVIE").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MOVIE"));
    }

    @Test
    void should_applyTypeAndStatusFiltersTogether_endToEnd() throws Exception {
        String token = registerAndLogin("library-list-combined-filter@example.com");
        Long userId = currentUserId(token, "library-list-combined-filter@example.com");
        seedMovie(userId, 1L, "Watched Movie", WatchStatus.WATCHED);
        seedMovie(userId, 2L, "Planned Movie", WatchStatus.PLANNED);
        seedSeries(userId, 3L, "Watched Series", WatchStatus.WATCHED);

        mockMvc.perform(get("/api/library?type=MOVIE&status=WATCHED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Watched Movie"));
    }

    @Test
    void should_returnOnlyTheRequestedPage_endToEnd() throws Exception {
        String token = registerAndLogin("library-list-pagination@example.com");
        Long userId = currentUserId(token, "library-list-pagination@example.com");
        seedMovie(userId, 1L, "Movie A", WatchStatus.PLANNED);
        seedMovie(userId, 2L, "Movie B", WatchStatus.PLANNED);
        seedMovie(userId, 3L, "Movie C", WatchStatus.PLANNED);

        mockMvc.perform(get("/api/library?page=2&pageSize=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void should_returnAnEmptyList_when_theUsersLibraryIsEmptyEndToEnd() throws Exception {
        String token = registerAndLogin("library-list-empty@example.com");

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void should_returnAnEmptyList_when_theFilterCombinationMatchesNothingEndToEnd() throws Exception {
        String token = registerAndLogin("library-list-no-match@example.com");
        Long userId = currentUserId(token, "library-list-no-match@example.com");
        seedMovie(userId, 1L, "Planned Movie", WatchStatus.PLANNED);

        mockMvc.perform(get("/api/library?status=WATCHED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void should_onlyReturnTheRequestingUsersOwnItems_when_twoUsersHaveLibraries() throws Exception {
        String tokenA = registerAndLogin("library-list-isolation-a@example.com");
        String tokenB = registerAndLogin("library-list-isolation-b@example.com");
        Long userIdA = currentUserId(tokenA, "library-list-isolation-a@example.com");
        Long userIdB = currentUserId(tokenB, "library-list-isolation-b@example.com");
        seedMovie(userIdA, 1L, "User A's Movie", WatchStatus.PLANNED);
        seedMovie(userIdB, 2L, "User B's Movie", WatchStatus.PLANNED);

        mockMvc.perform(get("/api/library").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("User A's Movie"));
    }

    @Test
    void should_stillPermitHealthWithoutAuthentication_when_libraryListRouteIsAdded() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void should_stillAuthenticateTheAuthMeEndpoint_when_libraryListRouteIsAdded() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        String token = registerAndLogin("library-list-me-unaffected@example.com");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("library-list-me-unaffected@example.com"));
    }

    private Long currentUserId(String token, String expectedEmail) throws Exception {
        MvcResult meResult = mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(expectedEmail))
                .andReturn();

        return objectMapper.readTree(meResult.getResponse().getContentAsString()).get("userId").asLong();
    }
}
