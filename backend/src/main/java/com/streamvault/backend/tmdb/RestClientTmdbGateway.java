package com.streamvault.backend.tmdb;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.streamvault.backend.tmdb.dto.TmdbEpisode;
import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.dto.TmdbResult;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
import com.streamvault.backend.tmdb.dto.TmdbSeason;
import com.streamvault.backend.tmdb.dto.TmdbSeries;
import com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * The only class that talks to TMDB. Constructor shape mirrors {@code GoogleTokenInfoVerifier} so it
 * is testable with {@code MockRestServiceServer.bindTo(builder)} - the {@link RestClient} is built
 * from the injected builder rather than a fresh one. Authenticates with the TMDB v3 {@code api_key}
 * query parameter. Maps {@code media_type} {@code movie} to {@code MOVIE} and {@code tv} to
 * {@code SERIES}, dropping every other {@code media_type}. Any {@link RestClientException} or non-2xx
 * response (both land in the single catch below, since {@code .retrieve()} throws on non-2xx by
 * default) is wrapped in {@link TmdbUnavailableException} with the original cause attached (AC-6).
 *
 * <p>{@code POPULAR} and {@code TRENDING} both hit a {@code /trending/all/*} endpoint, which returns
 * the same mixed movie/tv payload shape as {@code /search/multi}; that is what lets browse reuse the
 * search mapping verbatim (AC-3).
 */
@Component
public class RestClientTmdbGateway implements TmdbGateway {

    /** A leading 4-digit token; TMDB dates are ISO-8601 ({@code yyyy-MM-dd}) when present. */
    private static final Pattern LEADING_YEAR = Pattern.compile("^(\\d{4})");

    private static final TmdbResultPage EMPTY_PAGE = new TmdbResultPage(1, 0, 0, List.of());

    /** TMDB's own documented cap on {@code append_to_response} items per call. */
    private static final int MAX_SEASON_BATCH_SIZE = 20;

    private final RestClient restClient;
    private final String apiKey;
    private final String imageBaseUrl;

    public RestClientTmdbGateway(
            RestClient.Builder restClientBuilder,
            @Value("${tmdb.base-url}") String baseUrl,
            @Value("${tmdb.api-key}") String apiKey,
            @Value("${tmdb.image-base-url:https://image.tmdb.org/t/p/w500}") String imageBaseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.imageBaseUrl = imageBaseUrl;
    }

    @Override
    public TmdbResultPage search(String query, int page) {
        return fetch(uriBuilder -> uriBuilder
                .path("/search/multi")
                .queryParam("api_key", apiKey)
                .queryParam("query", query)
                .queryParam("page", page)
                .build());
    }

    @Override
    public TmdbResultPage browse(TmdbBrowseList list, int page) {
        String path = switch (list) {
            case POPULAR -> "/trending/all/week";
            case TRENDING -> "/trending/all/day";
        };
        return fetch(uriBuilder -> uriBuilder
                .path(path)
                .queryParam("api_key", apiKey)
                .queryParam("page", page)
                .build());
    }

    /**
     * Own small fetch rather than the shared {@link #fetch} helper: that helper is typed to
     * {@code TmdbResponse} and wraps every {@link RestClientException} (which includes every non-2xx,
     * since {@code .retrieve()} throws by default) into {@link TmdbUnavailableException}. This method
     * needs a finer split - a TMDB {@code 404} is a definite "no such movie"
     * ({@link TmdbTitleNotFoundException}, AC-7) while {@code 401} / {@code 403} / {@code 429} /
     * {@code 5xx} / transport failures stay {@link TmdbUnavailableException} (story-006 behaviour,
     * unchanged). {@code search} / {@code browse} are untouched.
     */
    @Override
    public TmdbMovie movie(long tmdbId) {
        TmdbMovieJson json;
        try {
            json = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/movie/{tmdbId}")
                            .queryParam("api_key", apiKey)
                            .build(tmdbId))
                    .retrieve()
                    .body(TmdbMovieJson.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new TmdbTitleNotFoundException(tmdbId);
        } catch (RestClientException ex) {
            throw new TmdbUnavailableException(ex);
        }
        if (json == null) {
            return new TmdbMovie(tmdbId, null, null, null);
        }
        String posterUrl = json.posterPath() == null ? null : imageBaseUrl + json.posterPath();
        return new TmdbMovie(tmdbId, json.title(), parseYear(json.releaseDate()), posterUrl);
    }

    /**
     * Own small fetch shape, same reasoning as {@link #movie(long)}: a {@code 404} on the series call
     * is a definite "no such series" ({@link TmdbSeriesNotFoundException}, AC-8), while any other
     * failure on the series call or on a later batched season call is
     * {@link TmdbUnavailableException} - the series id is already confirmed valid once the first call
     * succeeds, so a batch-call failure is an availability problem, not a "not found" one.
     *
     * <p>Implemented in up to {@code 1 + ceil(seasonCount / 20)} stages: one series-detail call, then
     * one {@code append_to_response} call per batch of at most 20 season numbers, in the order TMDB
     * lists them in the series-detail response (including season {@code 0}, AC-9). For the common
     * case of 20 or fewer seasons this is exactly 2 HTTP calls total.
     */
    @Override
    public TmdbSeries series(long tmdbId) {
        TmdbSeriesJson seriesJson;
        try {
            seriesJson = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tv/{tmdbId}")
                            .queryParam("api_key", apiKey)
                            .build(tmdbId))
                    .retrieve()
                    .body(TmdbSeriesJson.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new TmdbSeriesNotFoundException(tmdbId);
        } catch (RestClientException ex) {
            throw new TmdbUnavailableException(ex);
        }
        if (seriesJson == null) {
            return new TmdbSeries(tmdbId, null, null, null, List.of());
        }

        String posterUrl = seriesJson.posterPath() == null ? null : imageBaseUrl + seriesJson.posterPath();
        Integer firstAirYear = parseYear(seriesJson.firstAirDate());
        List<Integer> seasonNumbers = seriesJson.seasons() == null
                ? List.of()
                : seriesJson.seasons().stream().map(TmdbSeasonSummaryJson::seasonNumber).toList();

        if (seasonNumbers.isEmpty()) {
            return new TmdbSeries(tmdbId, seriesJson.name(), firstAirYear, posterUrl, List.of());
        }

        List<TmdbSeason> seasons = new ArrayList<>();
        for (List<Integer> batch : partitionIntoBatches(seasonNumbers, MAX_SEASON_BATCH_SIZE)) {
            JsonNode batchResponse = fetchSeasonBatch(tmdbId, batch);
            for (int seasonNumber : batch) {
                seasons.add(toSeason(seasonNumber, batchResponse));
            }
        }

        return new TmdbSeries(tmdbId, seriesJson.name(), firstAirYear, posterUrl, seasons);
    }

    private JsonNode fetchSeasonBatch(long tmdbId, List<Integer> seasonNumbers) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/tv/{tmdbId}")
                            .queryParam("api_key", apiKey)
                            .queryParam("append_to_response", appendToResponseParam(seasonNumbers))
                            .build(tmdbId))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new TmdbUnavailableException(ex);
        }
    }

    private String appendToResponseParam(List<Integer> seasonNumbers) {
        return seasonNumbers.stream()
                .map(n -> "season/" + n)
                .collect(Collectors.joining(","));
    }

    /**
     * Package-private and static so {@code RestClientTmdbGatewayBatchPartitioningTest} can pin the
     * chunking boundaries directly as a pure function, without going through
     * {@code MockRestServiceServer}.
     */
    static List<List<Integer>> partitionIntoBatches(List<Integer> seasonNumbers, int batchSize) {
        List<List<Integer>> batches = new ArrayList<>();
        for (int i = 0; i < seasonNumbers.size(); i += batchSize) {
            batches.add(seasonNumbers.subList(i, Math.min(i + batchSize, seasonNumbers.size())));
        }
        return batches;
    }

    private TmdbSeason toSeason(int seasonNumber, JsonNode batchResponse) {
        JsonNode seasonNode = batchResponse == null ? null : batchResponse.get("season/" + seasonNumber);
        List<TmdbEpisode> episodes = new ArrayList<>();
        if (seasonNode != null && seasonNode.has("episodes")) {
            for (JsonNode episodeNode : seasonNode.get("episodes")) {
                int episodeNumber = episodeNode.get("episode_number").asInt();
                JsonNode nameNode = episodeNode.get("name");
                String title = nameNode == null || nameNode.isNull() ? null : nameNode.asText();
                episodes.add(new TmdbEpisode(episodeNumber, title));
            }
        }
        return new TmdbSeason(seasonNumber, episodes);
    }

    private TmdbResultPage fetch(Function<UriBuilder, URI> uriFunction) {
        TmdbResponse response;
        try {
            response = restClient.get()
                    .uri(uriFunction)
                    .retrieve()
                    .body(TmdbResponse.class);
        } catch (RestClientException ex) {
            throw new TmdbUnavailableException(ex);
        }
        return toResultPage(response);
    }

    private TmdbResultPage toResultPage(TmdbResponse response) {
        if (response == null) {
            return EMPTY_PAGE;
        }
        List<TmdbResult> results = response.results() == null
                ? List.of()
                : response.results().stream()
                        .map(this::toResult)
                        .filter(Objects::nonNull)
                        .toList();
        return new TmdbResultPage(response.page(), response.totalPages(), response.totalResults(), results);
    }

    /** Returns {@code null} for any entry whose {@code media_type} is not {@code movie} or {@code tv}. */
    private TmdbResult toResult(TmdbResultJson json) {
        TmdbMediaType mediaType = switch (json.mediaType() == null ? "" : json.mediaType()) {
            case "movie" -> TmdbMediaType.MOVIE;
            case "tv" -> TmdbMediaType.SERIES;
            default -> null;
        };
        if (mediaType == null) {
            return null;
        }

        boolean isMovie = mediaType == TmdbMediaType.MOVIE;
        String title = isMovie ? json.title() : json.name();
        Integer releaseYear = parseYear(isMovie ? json.releaseDate() : json.firstAirDate());
        String posterUrl = json.posterPath() == null ? null : imageBaseUrl + json.posterPath();

        return new TmdbResult(json.id(), mediaType.name(), title, releaseYear, posterUrl);
    }

    /** 4-digit year from a leading {@code yyyy} token; {@code null} on missing, empty, or unparseable. */
    private Integer parseYear(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        Matcher matcher = LEADING_YEAR.matcher(date);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbResponse(
            int page,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_results") int totalResults,
            List<TmdbResultJson> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbMovieJson(
            long id,
            String title,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("poster_path") String posterPath) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbSeriesJson(
            long id,
            String name,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("poster_path") String posterPath,
            List<TmdbSeasonSummaryJson> seasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbSeasonSummaryJson(@JsonProperty("season_number") int seasonNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TmdbResultJson(
            long id,
            @JsonProperty("media_type") String mediaType,
            String title,
            String name,
            @JsonProperty("release_date") String releaseDate,
            @JsonProperty("first_air_date") String firstAirDate,
            @JsonProperty("poster_path") String posterPath) {
    }
}
