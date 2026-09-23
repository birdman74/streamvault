package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.streamvault.backend.tmdb.dto.TmdbSeries;
import com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. The new
 * {@code RestClientTmdbGateway#series(long)} method, {@code TmdbSeries}, {@code TmdbSeason},
 * {@code TmdbEpisode}, and {@code TmdbSeriesNotFoundException} do not exist yet; this test is
 * expected to fail to compile until Dev implements them.
 *
 * <p>Revised in design round 1 (see {@code story-008-test-revision-r1.md}) per Dev's feedback:
 * episode detail is fetched via one {@code GET /tv/{id}?append_to_response=season/{n1},season/{n2},...}
 * call per batch of up to 20 season numbers, instead of one {@code GET /tv/{id}/season/{n}} call per
 * season. Pins the series-level mapping (AC-3), the batched episode mapping and numbering (AC-2),
 * the 20-season batch boundary, season {@code 0} handling (AC-9), the null rules for
 * {@code firstAirYear} / {@code posterUrl} / episode {@code title}, and the failure split: a
 * {@code 404} on the series call becomes {@link TmdbSeriesNotFoundException} (AC-8), while every
 * other upstream failure on either the series call or a batch call stays
 * {@link TmdbUnavailableException}.
 */
class RestClientTmdbGatewaySeriesTest {

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
    void should_mapTheSeriesLevelCatalogData_when_tmdbReturnsTheSeries() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": []
                }
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.tmdbId()).isEqualTo(1399L);
        assertThat(series.title()).isEqualTo("Game of Thrones");
        assertThat(series.firstAirYear()).isEqualTo(2011);
        assertThat(series.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/got.jpg");
        assertThat(series.seasons()).isEmpty();
        server.verify();
    }

    @Test
    void should_fetchEpisodesForEverySeasonListedByTmdb_when_seriesHasMultipleSeasons() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [
                    {"season_number": 1},
                    {"season_number": 2}
                  ]
                }
                """);
        expectSeasonBatch(1399L, List.of(1, 2), """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "season/1": {"episodes": [{"episode_number": 1, "name": "Winter Is Coming"}]},
                  "season/2": {"episodes": [{"episode_number": 1, "name": "The North Remembers"}]}
                }
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons()).hasSize(2);
        assertThat(series.seasons().get(0).seasonNumber()).isEqualTo(1);
        assertThat(series.seasons().get(0).episodes()).hasSize(1);
        assertThat(series.seasons().get(1).seasonNumber()).isEqualTo(2);
        assertThat(series.seasons().get(1).episodes()).hasSize(1);
        server.verify();
    }

    @Test
    void should_preserveEpisodeAndSeasonNumbering_when_mappingTheStructure() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [{"season_number": 1}]
                }
                """);
        expectSeasonBatch(1399L, List.of(1), """
                {
                  "season/1": {
                    "episodes": [
                      {"episode_number": 1, "name": "Winter Is Coming"},
                      {"episode_number": 2, "name": "The Kingsroad"}
                    ]
                  }
                }
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons().get(0).episodes().get(0).episodeNumber()).isEqualTo(1);
        assertThat(series.seasons().get(0).episodes().get(0).title()).isEqualTo("Winter Is Coming");
        assertThat(series.seasons().get(0).episodes().get(1).episodeNumber()).isEqualTo(2);
        assertThat(series.seasons().get(0).episodes().get(1).title()).isEqualTo("The Kingsroad");
        server.verify();
    }

    @Test
    void should_mapEpisodeTitleAsNull_when_tmdbOmitsEpisodeName() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [{"season_number": 1}]
                }
                """);
        expectSeasonBatch(1399L, List.of(1), """
                {"season/1": {"episodes": [{"episode_number": 1}]}}
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons().get(0).episodes().get(0).title()).isNull();
        server.verify();
    }

    @Test
    void should_includeSeasonZeroAsItsOwnSeasonGrouping_when_tmdbListsSpecials() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [
                    {"season_number": 0},
                    {"season_number": 1}
                  ]
                }
                """);
        expectSeasonBatch(1399L, List.of(0, 1), """
                {
                  "season/0": {"episodes": [{"episode_number": 1, "name": "Series Recap"}]},
                  "season/1": {"episodes": [{"episode_number": 1, "name": "Winter Is Coming"}]}
                }
                """);

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons()).hasSize(2);
        assertThat(series.seasons().get(0).seasonNumber()).isEqualTo(0);
        assertThat(series.seasons().get(0).episodes().get(0).title()).isEqualTo("Series Recap");
        assertThat(series.seasons().get(1).seasonNumber()).isEqualTo(1);
        server.verify();
    }

    @Test
    void should_batchSeasonFetchesInGroupsOfAtMost20_when_seriesHasMoreThan20Seasons() {
        String seasonsSummary = IntStream.rangeClosed(1, 25)
                .mapToObj(n -> "{\"season_number\": " + n + "}")
                .collect(Collectors.joining(","));
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Long Runner",
                  "first_air_date": "1990-01-01",
                  "poster_path": "/lr.jpg",
                  "seasons": [%s]
                }
                """.formatted(seasonsSummary));

        String firstBatchSeasons = IntStream.rangeClosed(1, 20)
                .mapToObj(n -> "\"season/" + n + "\": {\"episodes\": [{\"episode_number\": 1, \"name\": \"S" + n + "E1\"}]}")
                .collect(Collectors.joining(","));
        expectSeasonBatch(1399L, IntStream.rangeClosed(1, 20).boxed().toList(),
                "{" + firstBatchSeasons + "}");

        String secondBatchSeasons = IntStream.rangeClosed(21, 25)
                .mapToObj(n -> "\"season/" + n + "\": {\"episodes\": [{\"episode_number\": 1, \"name\": \"S" + n + "E1\"}]}")
                .collect(Collectors.joining(","));
        expectSeasonBatch(1399L, IntStream.rangeClosed(21, 25).boxed().toList(),
                "{" + secondBatchSeasons + "}");

        TmdbSeries series = gateway.series(1399L);

        assertThat(series.seasons()).hasSize(25);
        assertThat(series.seasons().get(0).seasonNumber()).isEqualTo(1);
        assertThat(series.seasons().get(19).seasonNumber()).isEqualTo(20);
        assertThat(series.seasons().get(20).seasonNumber()).isEqualTo(21);
        assertThat(series.seasons().get(24).seasonNumber()).isEqualTo(25);
        assertThat(series.seasons().get(24).episodes().get(0).title()).isEqualTo("S25E1");
        server.verify();
    }

    @Test
    void should_returnNullPoster_when_tmdbOmitsPosterPath() {
        expectSeries(603L, """
                {"id": 603, "name": "No Poster Show", "first_air_date": "2015-01-01", "seasons": []}
                """);

        assertThat(gateway.series(603L).posterUrl()).isNull();
        server.verify();
    }

    @Test
    void should_returnNullFirstAirYear_when_tmdbFirstAirDateIsMissingOrEmpty() {
        expectSeries(1L, """
                {"id": 1, "name": "Undated", "first_air_date": "", "seasons": []}
                """);

        assertThat(gateway.series(1L).firstAirYear()).isNull();
        server.verify();
    }

    @Test
    void should_throwTmdbSeriesNotFoundException_when_tmdbReturns404ForTheSeriesId() {
        server.expect(requestTo(containsString("/tv/9999999")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"status_code\":34,\"status_message\":\"The resource you requested could not be found.\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.series(9_999_999L))
                .isInstanceOf(TmdbSeriesNotFoundException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbReturnsServerErrorForTheSeriesCall() {
        server.expect(requestTo(containsString("/tv/1399")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.series(1399L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_tmdbConnectionFailsOnTheSeriesCall() {
        server.expect(requestTo(containsString("/tv/1399")))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> gateway.series(1399L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_theSeasonBatchCallFails() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [{"season_number": 1}]
                }
                """);
        server.expect(requestTo(allOf(
                        containsString("/tv/1399"),
                        containsString("append_to_response=season/1"))))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.series(1399L))
                .isInstanceOf(TmdbUnavailableException.class);
    }

    @Test
    void should_throwTmdbUnavailableException_when_theSeasonBatchCallReturns404() {
        expectSeries(1399L, """
                {
                  "id": 1399,
                  "name": "Game of Thrones",
                  "first_air_date": "2011-04-17",
                  "poster_path": "/got.jpg",
                  "seasons": [{"season_number": 1}]
                }
                """);
        server.expect(requestTo(allOf(
                        containsString("/tv/1399"),
                        containsString("append_to_response=season/1"))))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"status_code\":34,\"status_message\":\"not found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.series(1399L))
                .isInstanceOf(TmdbUnavailableException.class);
    }
}
