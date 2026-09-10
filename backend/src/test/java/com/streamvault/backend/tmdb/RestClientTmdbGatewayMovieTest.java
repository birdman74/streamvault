package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. The new
 * {@code RestClientTmdbGateway#movie(long)} method, {@code TmdbMovie}, and
 * {@code TmdbTitleNotFoundException} do not exist yet; this test is expected to fail to compile until
 * Dev implements them.
 *
 * <p>Same {@link MockRestServiceServer} pattern as {@code RestClientTmdbGatewayTest}: binds to the
 * {@link RestClient.Builder} the gateway is constructed with, so no live network call is made. This
 * pins the single-movie projection for {@code GET /movie/{id}} - id / title / year / poster mapping
 * reusing story-006's exact null rules (AC-2) - and the failure split that AC-7 depends on: a TMDB
 * {@code 404} becomes {@link TmdbTitleNotFoundException} (definite "no such movie"), while every
 * other upstream failure stays {@link TmdbUnavailableException} (story-006 behaviour, unchanged).
 */
class RestClientTmdbGatewayMovieTest {

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
    void should_mapTheSingleMovieProjection_when_tmdbReturnsTheMovie() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": 27205,
                          "title": "Inception",
                          "release_date": "2010-07-15",
                          "poster_path": "/inception.jpg"
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbMovie movie = gateway.movie(27205L);

        assertThat(movie.tmdbId()).isEqualTo(27205L);
        assertThat(movie.title()).isEqualTo("Inception");
        assertThat(movie.releaseYear()).isEqualTo(2010);
        assertThat(movie.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");
    }

    @Test
    void should_sendApiKeyToTheMovieEndpoint_when_movieIsCalled() {
        server.expect(requestTo(containsString("/movie/550")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("api_key", API_KEY))
                .andRespond(withSuccess("""
                        {"id": 550, "title": "Fight Club", "release_date": "1999-10-15"}
                        """, MediaType.APPLICATION_JSON));

        gateway.movie(550L);

        server.verify();
    }

    @Test
    void should_returnNullPoster_when_tmdbOmitsPosterPath() {
        server.expect(requestTo(containsString("/movie/603")))
                .andRespond(withSuccess("""
                        {"id": 603, "title": "The Matrix", "release_date": "1999-03-30"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.movie(603L).posterUrl()).isNull();
    }

    @Test
    void should_returnNullYear_when_tmdbReleaseDateIsMissingOrEmpty() {
        server.expect(requestTo(containsString("/movie/1")))
                .andRespond(withSuccess("""
                        {"id": 1, "title": "Undated", "release_date": ""}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.movie(1L).releaseYear()).isNull();
    }

    @Test
    void should_returnNullYear_when_tmdbReleaseDateIsUnparseable() {
        server.expect(requestTo(containsString("/movie/2")))
                .andRespond(withSuccess("""
                        {"id": 2, "title": "Weird Date", "release_date": "not-a-date"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(gateway.movie(2L).releaseYear()).isNull();
    }

    @Test
    void should_throwTmdbTitleNotFoundException_when_tmdbReturns404ForTheId() {
        server.expect(requestTo(containsString("/movie/9999999")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"status_code\":34,\"status_message\":\"The resource you requested could not be found.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.movie(9_999_999L))
                .isInstanceOf(TmdbTitleNotFoundException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsServerError() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.movie(27205L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbConnectionFails() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> gateway.movie(27205L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsUnauthorizedForABadKey() {
        server.expect(requestTo(containsString("/movie/27205")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"status_code\":7,\"status_message\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.movie(27205L))
                .isInstanceOf(TmdbUnavailableException.class);
    }
}
