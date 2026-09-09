package com.streamvault.backend.tmdb;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.streamvault.backend.tmdb.dto.TmdbResult;
import com.streamvault.backend.tmdb.dto.TmdbResultPage;
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
