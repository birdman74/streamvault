package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev unit test guarding the wire values of {@link TmdbMediaType} and {@link TmdbBrowseList}. The
 * mapper in {@link RestClientTmdbGateway} and the {@code list} query-parameter binding both depend
 * on the exact constant names; a later rename would silently change the API contract, so pin the
 * {@code name()} round-trip here (per {@code story-006-agreed.md} "Test Coverage Confirmation").
 */
class TmdbWireEnumTest {

    @Test
    void mediaType_wireValues_areExactlyMovieAndSeries() {
        assertThat(TmdbMediaType.MOVIE.name()).isEqualTo("MOVIE");
        assertThat(TmdbMediaType.SERIES.name()).isEqualTo("SERIES");
        assertThat(TmdbMediaType.valueOf("MOVIE")).isEqualTo(TmdbMediaType.MOVIE);
        assertThat(TmdbMediaType.valueOf("SERIES")).isEqualTo(TmdbMediaType.SERIES);
        assertThat(TmdbMediaType.values()).containsExactly(TmdbMediaType.MOVIE, TmdbMediaType.SERIES);
    }

    @Test
    void browseList_wireValues_areExactlyPopularAndTrending() {
        assertThat(TmdbBrowseList.POPULAR.name()).isEqualTo("POPULAR");
        assertThat(TmdbBrowseList.TRENDING.name()).isEqualTo("TRENDING");
        assertThat(TmdbBrowseList.valueOf("POPULAR")).isEqualTo(TmdbBrowseList.POPULAR);
        assertThat(TmdbBrowseList.valueOf("TRENDING")).isEqualTo(TmdbBrowseList.TRENDING);
        assertThat(TmdbBrowseList.values()).containsExactly(TmdbBrowseList.POPULAR, TmdbBrowseList.TRENDING);
    }
}
