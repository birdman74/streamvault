package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Dev-authored lower-level unit tests below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Adds two cases the
 * Test-authored {@code RestClientTmdbGatewayMovieTest} does not pin:
 *
 * <ul>
 *   <li>a {@code 403 Forbidden} (distinct from the 401 / 404 / 500 that suite already covers) stays
 *       {@link TmdbUnavailableException} - only a {@code 404} maps to
 *       {@link TmdbTitleNotFoundException};</li>
 *   <li>a 2xx with an empty {@code {}} body maps to a {@link TmdbMovie} with null fields and no NPE -
 *       the gateway must never 500 on a thin payload.</li>
 * </ul>
 */
class RestClientTmdbGatewayMovieEdgeCasesTest {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String API_KEY = "test-tmdb-key";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private MockRestServiceServer server;
    private RestClientTmdbGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RestClientTmdbGateway(builder, BASE_URL, API_KEY, IMAGE_BASE_URL);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsForbidden() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .body("{\"status_code\":7,\"status_message\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.movie(27205L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_returnAMovieWithNullFields_when_tmdbReturnsAnEmptyJsonBody() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        TmdbMovie movie = gateway.movie(27205L);

        assertThat(movie.tmdbId()).isEqualTo(27205L);
        assertThat(movie.title()).isNull();
        assertThat(movie.releaseYear()).isNull();
        assertThat(movie.posterUrl()).isNull();
    }
}
