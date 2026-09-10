package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.tmdb.dto.TmdbResultPage;

/**
 * Dev lower-level unit tests for {@link RestClientTmdbGateway}, below the boundary
 * {@code RestClientTmdbGatewayTest} (Test-authored) pins. Covers the year-parsing edge cases,
 * payload ordering after the person drop, and the defensive empty-results handling called out in
 * {@code docs/specs/design/story-006-agreed.md} "Test Coverage Confirmation". Same
 * {@code MockRestServiceServer.bindTo(builder)} setup as the Test-authored class.
 */
class RestClientTmdbGatewayEdgeCasesTest {

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
    void should_returnNullYear_when_dateIsAFullNonDateString() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 1,
                          "results": [
                            {"id": 10, "media_type": "movie", "title": "Unknown Date",
                             "release_date": "unknown"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("unknown date", 1);

        assertThat(page.results().get(0).releaseYear()).isNull();
    }

    @Test
    void should_returnNullYear_when_dateFieldIsJsonNull() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 1,
                          "results": [
                            {"id": 11, "media_type": "tv", "name": "Null Date", "first_air_date": null}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("null date", 1);

        assertThat(page.results().get(0).releaseYear()).isNull();
    }

    @Test
    void should_returnNullYear_when_dateLeadingTokenIsNotAFourDigitYear() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 1,
                          "results": [
                            {"id": 12, "media_type": "movie", "title": "Day First",
                             "release_date": "31-12-2021"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("day first", 1);

        assertThat(page.results().get(0).releaseYear()).isNull();
    }

    @Test
    void should_preservePayloadOrder_when_peopleAreDroppedFromTheMiddle() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {
                          "page": 1, "total_pages": 1, "total_results": 4,
                          "results": [
                            {"id": 1, "media_type": "movie", "title": "First", "release_date": "2001-01-01"},
                            {"id": 2, "media_type": "person", "name": "A Person"},
                            {"id": 3, "media_type": "tv", "name": "Second", "first_air_date": "2003-01-01"},
                            {"id": 4, "media_type": "movie", "title": "Third", "release_date": "2004-01-01"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("ordering", 1);

        assertThat(page.results()).extracting("title")
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void should_returnEmptyResults_when_tmdbOmitsTheResultsArray() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {"page": 1, "total_pages": 0, "total_results": 0}
                        """, MediaType.APPLICATION_JSON));

        TmdbResultPage page = gateway.search("no results key", 1);

        assertThat(page.results()).isEmpty();
    }

    @Test
    void should_returnEmptyResults_when_tmdbSendsResultsAsJsonNull() {
        server.expect(requestTo(Matchers.containsString("/search/multi")))
                .andRespond(withSuccess("""
                        {"page": 1, "total_pages": 0, "total_results": 0, "results": null}
                        """, MediaType.APPLICATION_JSON));

        assertThatCode(() -> {
            TmdbResultPage page = gateway.search("null results", 1);
            assertThat(page.results()).isEmpty();
        }).doesNotThrowAnyException();
    }
}
