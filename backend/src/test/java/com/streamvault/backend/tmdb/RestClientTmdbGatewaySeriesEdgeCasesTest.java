package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.tmdb.dto.TmdbSeries;

/**
 * Dev-authored lower-level unit tests below Test's integration boundary (see
 * docs/specs/design/story-008-agreed.md "Test Coverage Confirmation"). Adds the thin/absent-payload
 * cases the Test-authored {@code RestClientTmdbGatewaySeriesTest} does not pin, mirroring the
 * reasoning in story-007's {@code RestClientTmdbGatewayMovieEdgeCasesTest}: the gateway must never
 * NPE or 500 on a 2xx response that is missing data TMDB normally sends.
 *
 * <ul>
 *   <li>a {@code 204 No Content} on the series call itself (no body to deserialize) maps to a
 *       {@link TmdbSeries} with null fields and no seasons;</li>
 *   <li>a series payload that omits the {@code seasons} field entirely (as opposed to sending an
 *       empty array) still maps to no seasons;</li>
 *   <li>a {@code 204 No Content} on a season batch call leaves every season in that batch with an
 *       empty episode list rather than throwing;</li>
 *   <li>a season entry present in the batch response but missing its {@code episodes} field maps to
 *       an empty episode list for that season;</li>
 *   <li>an episode whose {@code name} is explicit JSON {@code null} (as opposed to the key being
 *       absent, which the Test-authored suite already covers) maps to a null title.</li>
 * </ul>
 */
class RestClientTmdbGatewaySeriesEdgeCasesTest {

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

    private void expectSeries(long tmdbId, String body) {
        server.expect(requestTo(containsString("/tv/" + tmdbId)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String appendToResponseParam(List<Integer> seasonNumbers) {
        return seasonNumbers.stream()
                .map(n -> "season/" + n)
                .collect(Collectors.joining(","));
    }

    private void expectSeasonBatch(long tmdbId, List<Integer> seasonNumbers, String body) {
        server.expect(requestTo(allOf(
                        containsString("/tv/" + tmdbId),
                        containsString("append_to_response=" + appendToResponseParam(seasonNumbers)))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void should_returnASeriesWithNullFieldsAndNoSeasons_when_tmdbReturnsNoContentForTheSeriesCall() {
        server.expect(requestTo(containsString("/tv/1399")))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.tmdbId()).isEqualTo(1399L);
        assertThat(series.title()).isNull();
        assertThat(series.firstAirYear()).isNull();
        assertThat(series.posterUrl()).isNull();
        assertThat(series.seasons()).isEmpty();
        server.verify();
    }

    @Test
    void should_returnNoSeasons_when_tmdbOmitsTheSeasonsFieldEntirely() {
        expectSeries(1399L, """
                {"id": 1399, "name": "Game of Thrones", "first_air_date": "2011-04-17", "poster_path": "/got.jpg"}
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons()).isEmpty();
        server.verify();
    }

    @Test
    void should_returnEmptyEpisodesForEverySeasonInTheBatch_when_theSeasonBatchCallReturnsNoContent() {
        expectSeries(1399L, """
                {"id": 1399, "name": "Game of Thrones", "first_air_date": "2011-04-17", "poster_path": "/got.jpg",
                 "seasons": [{"season_number": 1}]}
                """);
        server.expect(requestTo(allOf(
                        containsString("/tv/1399"),
                        containsString("append_to_response=season/1"))))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons()).hasSize(1);
        assertThat(series.seasons().get(0).seasonNumber()).isEqualTo(1);
        assertThat(series.seasons().get(0).episodes()).isEmpty();
        server.verify();
    }

    @Test
    void should_returnEmptyEpisodes_when_theBatchResponseOmitsTheEpisodesFieldForASeason() {
        expectSeries(1399L, """
                {"id": 1399, "name": "Game of Thrones", "first_air_date": "2011-04-17", "poster_path": "/got.jpg",
                 "seasons": [{"season_number": 1}]}
                """);
        expectSeasonBatch(1399L, List.of(1), """
                {"season/1": {}}
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons().get(0).episodes()).isEmpty();
        server.verify();
    }

    @Test
    void should_mapEpisodeTitleAsNull_when_tmdbSendsTheEpisodeNameAsExplicitJsonNull() {
        expectSeries(1399L, """
                {"id": 1399, "name": "Game of Thrones", "first_air_date": "2011-04-17", "poster_path": "/got.jpg",
                 "seasons": [{"season_number": 1}]}
                """);
        expectSeasonBatch(1399L, List.of(1), """
                {"season/1": {"episodes": [{"episode_number": 1, "name": null}]}}
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons().get(0).episodes().get(0).title()).isNull();
        server.verify();
    }
}
