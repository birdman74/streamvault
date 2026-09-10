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

import com.streamvault.backend.tmdb.dto.TmdbResult;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-006-api-contracts.md. {@code RestClientTmdbGateway},
 * {@code TmdbBrowseList}, {@code TmdbResult}, {@code TmdbResultPage}, and
 * {@code TmdbUnavailableException} do not exist yet; this test is expected to fail to compile until
 * Dev implements them.
 *
 * <p>Direct copy of the {@code GoogleTokenInfoVerifierTest} pattern -- binds a
 * {@link MockRestServiceServer} to the {@link RestClient.Builder} the gateway is constructed with,
 * so no live network call is made and every case is deterministic. This is where the TMDB JSON to
 * {@link TmdbResultPage} mapping is pinned: mixed movie/tv/person payloads and person filtering
 * (AC-1), title/year/poster field selection (AC-2), pagination envelope pass-through (AC-4), the
 * zero-results payload (AC-5), browse endpoint routing (AC-3), and the failure modes that must all
 * wrap to {@link TmdbUnavailableException} (AC-6).
 */
class RestClientTmdbGatewayTest {

    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String API_KEY = "test-tmdb-key";
    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

    private static final String EMPTY_PAGE_JSON = """
            {"page": 1, "total_pages": 0, "total_results": 0, "results": []}
            """;

    private MockRestServiceServer server;
    private RestClientTmdbGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RestClientTmdbGateway(builder, BASE_URL, API_KEY, IMAGE_BASE_URL);
    }

    @Test
    void should_mapMoviesAndSeriesAndDropPeople_when_tmdbMultiSearchReturnsMixedResults() {
        server.expect(requestTo(containsString("/search/multi")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "page": 1,
                          "total_pages": 1,
                          "total_results": 3,
                          "results": [
                            {
                              "id": 27205,
                              "media_type": "movie",
                              "title": "Inception",
                              "release_date": "2010-07-15",
                              "poster_path": "/inception.jpg"
                            },
                            {
                              "id": 1399,
                              "media_type": "tv",
                              "name": "Game of Thrones",
                              "first_air_date": "2011-04-17",
                              "poster_path": null
                            },
                            {
                              "id": 500,
                              "media_type": "person",
                              "name": "Tom Hardy"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("inception", 1);

        assertThat(page.results()).hasSize(2);

        TmdbResult movie = page.results().get(0);
        assertThat(movie.tmdbId()).isEqualTo(27205L);
        assertThat(movie.mediaType()).isEqualTo("MOVIE");
        assertThat(movie.title()).isEqualTo("Inception");
        assertThat(movie.releaseYear()).isEqualTo(2010);
        assertThat(movie.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");

        TmdbResult series = page.results().get(1);
        assertThat(series.tmdbId()).isEqualTo(1399L);
        assertThat(series.mediaType()).isEqualTo("SERIES");
        assertThat(series.title()).isEqualTo("Game of Thrones");
        assertThat(series.releaseYear()).isEqualTo(2011);
        assertThat(series.posterUrl()).isNull();

        assertThat(page.results()).noneMatch(r -> "Tom Hardy".equals(r.title()));
    }

    @Test
    void should_sendApiKeyAndQueryToTmdb_when_searchIsCalled() {
        server.expect(requestTo(containsString("/search/multi")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", "inception"))
                .andExpect(queryParam("api_key", API_KEY))
                .andRespond(withSuccess(EMPTY_PAGE_JSON, MediaType.APPLICATION_JSON));

        gateway.search("inception", 1);

        server.verify();
    }

    @Test
    void should_buildAnAbsolutePosterUrl_when_tmdbProvidesAPosterPath() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 1,
                          "results": [
                            {"id": 1, "media_type": "movie", "title": "Dune",
                             "release_date": "2021-09-15", "poster_path": "/dune.jpg"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("dune", 1);

        assertThat(page.results().get(0).posterUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w500/dune.jpg");
    }

    @Test
    void should_returnNullPoster_when_tmdbOmitsPosterPath() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 1,
                          "results": [
                            {"id": 2, "media_type": "movie", "title": "No Poster Movie",
                             "release_date": "2000-01-01"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("no poster", 1);

        assertThat(page.results().get(0).posterUrl()).isNull();
    }

    @Test
    void should_returnNullYear_when_tmdbDateIsMissingOrEmpty() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 2,
                          "results": [
                            {"id": 3, "media_type": "movie", "title": "Undated", "release_date": ""},
                            {"id": 4, "media_type": "tv", "name": "Also Undated"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("undated", 1);

        assertThat(page.results().get(0).releaseYear()).isNull();
        assertThat(page.results().get(1).releaseYear()).isNull();
    }

    @Test
    void should_carryTmdbPaginationMetadata_when_mappingASearchResponse() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 3, "total_pages": 12, "total_results": 231,
                          "results": [
                            {"id": 5, "media_type": "movie", "title": "Result", "release_date": "2020-01-01"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("result", 3);

        assertThat(page.page()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(12);
        assertThat(page.totalResults()).isEqualTo(231);
    }

    @Test
    void should_returnExplicitEmptyPage_when_tmdbReturnsZeroResults() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withSuccess(EMPTY_PAGE_JSON, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("zzznothingmatches", 1);

        assertThat(page.results()).isEmpty();
        assertThat(page.totalResults()).isZero();
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsServerError() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.search("inception", 1))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbConnectionFails() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> gateway.search("inception", 1))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsUnauthorizedForABadKey() {
        server.expect(requestTo(containsString("/search/multi")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"status_code\":7,\"status_message\":\"Invalid API key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.search("inception", 1))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_requestWeeklyTrendingEndpoint_when_browseListIsPopular() {
        server.expect(requestTo(containsString("/trending/all/week")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("api_key", API_KEY))
                .andRespond(withSuccess(EMPTY_PAGE_JSON, MediaType.APPLICATION_JSON));

        gateway.browse(TmdbBrowseList.POPULAR, 1);

        server.verify();
    }

    @Test
    void should_requestDailyTrendingEndpoint_when_browseListIsTrending() {
        server.expect(requestTo(containsString("/trending/all/day")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(EMPTY_PAGE_JSON, MediaType.APPLICATION_JSON));

        gateway.browse(TmdbBrowseList.TRENDING, 1);

        server.verify();
    }

    @Test
    void should_mapBrowseResponseToTheSameProjectionAsSearch_when_browsing() {
        server.expect(requestTo(containsString("/trending/all/week")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 500, "total_results": 10000,
                          "results": [
                            {"id": 1184918, "media_type": "movie", "title": "The Wild Robot",
                             "release_date": "2024-09-12", "poster_path": "/wildrobot.jpg"},
                            {"id": 94997, "media_type": "tv", "name": "House of the Dragon",
                             "first_air_date": "2022-08-21", "poster_path": null},
                            {"id": 200, "media_type": "person", "name": "Someone"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.browse(TmdbBrowseList.POPULAR, 1);

        assertThat(page.results()).hasSize(2);
        assertThat(page.results().get(0).mediaType()).isEqualTo("MOVIE");
        assertThat(page.results().get(0).title()).isEqualTo("The Wild Robot");
        assertThat(page.results().get(0).releaseYear()).isEqualTo(2024);
        assertThat(page.results().get(0).posterUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w500/wildrobot.jpg");
        assertThat(page.results().get(1).mediaType()).isEqualTo("SERIES");
        assertThat(page.results().get(1).title()).isEqualTo("House of the Dragon");
        assertThat(page.results()).noneMatch(r -> "Someone".equals(r.title()));
    }
}
