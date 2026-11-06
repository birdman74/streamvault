package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.streamvault.backend.library.dto.LibraryItemResponse;
import com.streamvault.backend.library.dto.LibraryListResponse;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. None of {@code LibraryService},
 * {@code LibraryItemType}, {@code WatchStatusRollup}, {@code LibraryItemResponse}, or
 * {@code LibraryListResponse} exist yet, and neither repository has a {@code findByUserId} method
 * yet; this test is expected to fail to compile until Dev implements them.
 *
 * <p>Mockito, mocks both repositories, mirrors {@code LibraryMovieServiceTest} /
 * {@code LibrarySeriesServiceTest}. The home for the merge/filter/sort/paginate algorithm: type
 * filtering (AC-3) including that the excluded repository is never even queried, status filtering
 * (AC-4) including the any-of-several case, type+status combined as AND (AC-5), pagination math and
 * the beyond-last-page empty case (AC-6, AC-8), empty results (AC-8), strict user-scoping of both
 * repository calls (AC-7), the sort order, and that a series' status is always the
 * {@code WatchStatusRollup} value, never a raw episode value.
 *
 * <p>Fixture entities use each entity's existing public constructor and {@code addSeason} /
 * {@code addEpisode} helpers, then {@link ReflectionTestUtils} sets the private, setter-less
 * {@code id} and {@code addedAt} fields so pagination and sort-order assertions are deterministic -
 * both entities only stamp {@code addedAt = Instant.now()} and leave {@code id} {@code null} until
 * JPA assigns it, which a non-persisted Mockito fixture never does.
 */
@ExtendWith(MockitoExtension.class)
class LibraryServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private LibraryMovieRepository libraryMovieRepository;

    @Mock
    private LibrarySeriesRepository librarySeriesRepository;

    private LibraryService libraryService;

    @BeforeEach
    void setUp() {
        libraryService = new LibraryService(libraryMovieRepository, librarySeriesRepository);
    }

    private LibraryMovie movie(long id, long tmdbId, String title, Integer year, String poster,
            WatchStatus status, Instant addedAt) {
        LibraryMovie movie = new LibraryMovie(USER_ID, tmdbId, title, year, poster, status);
        ReflectionTestUtils.setField(movie, "id", id);
        ReflectionTestUtils.setField(movie, "addedAt", addedAt);
        return movie;
    }

    private LibrarySeries series(long id, long tmdbId, String title, Integer year, String poster,
            Instant addedAt, WatchStatus... episodeStatuses) {
        LibrarySeries series = new LibrarySeries(USER_ID, tmdbId, title, year, poster);
        ReflectionTestUtils.setField(series, "id", id);
        ReflectionTestUtils.setField(series, "addedAt", addedAt);

        LibrarySeason season = new LibrarySeason(1);
        int episodeNumber = 1;
        for (WatchStatus status : episodeStatuses) {
            season.addEpisode(new LibraryEpisode(episodeNumber++, "Episode", status));
        }
        series.addSeason(season);
        return series;
    }

    private void stubEmptyRepositories() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());
    }

    // ---- AC-1 / AC-3: type filtering ----

    @Test
    void should_returnBothMoviesAndSeries_when_noTypeFilterIsApplied() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 27205L, "Inception", 2010, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(1L, 1399L, "Game of Thrones", 2011, null, Instant.parse("2026-11-02T00:00:00Z"), WatchStatus.PLANNED)));

        LibraryListResponse response = libraryService.list(USER_ID, null, null, null, null);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).extracting(LibraryItemResponse::type)
                .containsExactlyInAnyOrder("MOVIE", "SERIES");
    }

    @Test
    void should_returnOnlyMovies_when_typeFilterIsMovieOnly() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 27205L, "Inception", 2010, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z"))));

        LibraryListResponse response = libraryService.list(
                USER_ID, List.of(LibraryItemType.MOVIE), null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).type()).isEqualTo("MOVIE");
    }

    @Test
    void should_returnOnlySeries_when_typeFilterIsSeriesOnly() {
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(1L, 1399L, "Game of Thrones", 2011, null, Instant.parse("2026-11-02T00:00:00Z"), WatchStatus.PLANNED)));

        LibraryListResponse response = libraryService.list(
                USER_ID, List.of(LibraryItemType.SERIES), null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).type()).isEqualTo("SERIES");
    }

    @Test
    void should_notQueryTheSeriesRepositoryAtAll_when_typeFilterIsMovieOnly() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of());

        libraryService.list(USER_ID, List.of(LibraryItemType.MOVIE), null, null, null);

        verifyNoInteractions(librarySeriesRepository);
    }

    @Test
    void should_notQueryTheMovieRepositoryAtAll_when_typeFilterIsSeriesOnly() {
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        libraryService.list(USER_ID, List.of(LibraryItemType.SERIES), null, null, null);

        verifyNoInteractions(libraryMovieRepository);
    }

    // ---- AC-2: item shape / series rollup status ----

    @Test
    void should_mapAMoviesStoredStatus_when_buildingAnItem() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(5L, 550L, "Fight Club", 1999, "https://img/fc.jpg",
                        WatchStatus.CURRENTLY_WATCHING, Instant.parse("2026-11-01T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryItemResponse item = libraryService.list(USER_ID, null, null, null, null).items().get(0);

        assertThat(item.id()).isEqualTo(5L);
        assertThat(item.type()).isEqualTo("MOVIE");
        assertThat(item.tmdbId()).isEqualTo(550L);
        assertThat(item.title()).isEqualTo("Fight Club");
        assertThat(item.year()).isEqualTo(1999);
        assertThat(item.posterUrl()).isEqualTo("https://img/fc.jpg");
        assertThat(item.status()).isEqualTo("CURRENTLY_WATCHING");
        assertThat(item.addedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void should_mapASeriesStatusAsItsWatchStatusRollupValue_when_buildingAnItem() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(3L, 1399L, "Game of Thrones", 2011, "https://img/got.jpg",
                        Instant.parse("2026-11-02T00:00:00Z"), WatchStatus.WATCHED, WatchStatus.PLANNED)));

        LibraryItemResponse item = libraryService.list(USER_ID, null, null, null, null).items().get(0);

        assertThat(item.id()).isEqualTo(3L);
        assertThat(item.type()).isEqualTo("SERIES");
        assertThat(item.tmdbId()).isEqualTo(1399L);
        assertThat(item.title()).isEqualTo("Game of Thrones");
        assertThat(item.year()).isEqualTo(2011);
        assertThat(item.posterUrl()).isEqualTo("https://img/got.jpg");
        assertThat(item.status()).isEqualTo("CURRENTLY_WATCHING");
    }

    // ---- AC-4: status filtering ----

    @Test
    void should_returnOnlyItemsMatchingASingleRequestedStatus_when_oneStatusIsRequested() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "Watched Movie", 2000, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z")),
                movie(2L, 2L, "Planned Movie", 2001, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(
                USER_ID, null, List.of(WatchStatus.WATCHED), null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Watched Movie");
    }

    @Test
    void should_returnItemsMatchingAnyOfSeveralRequestedStatuses_when_multipleStatusesAreRequested() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "Watched Movie", 2000, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z")),
                movie(2L, 2L, "Planned Movie", 2001, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z")),
                movie(3L, 3L, "Watching Movie", 2002, null, WatchStatus.CURRENTLY_WATCHING, Instant.parse("2026-11-03T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(
                USER_ID, null, List.of(WatchStatus.WATCHED, WatchStatus.PLANNED), null, null);

        assertThat(response.items()).extracting(LibraryItemResponse::title)
                .containsExactlyInAnyOrder("Watched Movie", "Planned Movie");
    }

    @Test
    void should_returnEveryStatus_when_noStatusFilterIsApplied() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "A", 2000, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z")),
                movie(2L, 2L, "B", 2001, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z")),
                movie(3L, 3L, "C", 2002, null, WatchStatus.CURRENTLY_WATCHING, Instant.parse("2026-11-03T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(USER_ID, null, null, null, null);

        assertThat(response.items()).hasSize(3);
    }

    @Test
    void should_filterASeriesByItsRolledUpStatus_notByAnyRawEpisodeStatus() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of());
        // Mixed episodes (one WATCHED, one PLANNED) roll up to CURRENTLY_WATCHING; the raw
        // episode-level WATCHED value must not leak into a WATCHED-only filter match.
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(1L, 1399L, "Mixed Series", 2011, null,
                        Instant.parse("2026-11-01T00:00:00Z"), WatchStatus.WATCHED, WatchStatus.PLANNED)));

        LibraryListResponse watchedOnly = libraryService.list(
                USER_ID, null, List.of(WatchStatus.WATCHED), null, null);
        LibraryListResponse currentlyWatchingOnly = libraryService.list(
                USER_ID, null, List.of(WatchStatus.CURRENTLY_WATCHING), null, null);

        assertThat(watchedOnly.items()).isEmpty();
        assertThat(currentlyWatchingOnly.items()).hasSize(1);
    }

    // ---- AC-5: type + status combined ----

    @Test
    void should_applyTypeAndStatusFiltersTogetherAsAnAndCondition_when_bothAreRequested() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "Watched Movie", 2000, null, WatchStatus.WATCHED, Instant.parse("2026-11-01T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(1L, 2L, "Watched Series", 2001, null, Instant.parse("2026-11-02T00:00:00Z"), WatchStatus.WATCHED)));

        LibraryListResponse response = libraryService.list(
                USER_ID, List.of(LibraryItemType.MOVIE), List.of(WatchStatus.WATCHED), null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).type()).isEqualTo("MOVIE");
    }

    // ---- AC-6: pagination ----

    @Test
    void should_capTheReturnedItemsAtPageSize_when_moreItemsExistThanOnePageHolds() {
        List<LibraryMovie> movies = List.of(
                movie(1L, 1L, "A", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-01T00:00:00Z")),
                movie(2L, 2L, "B", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z")),
                movie(3L, 3L, "C", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-03T00:00:00Z")));
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(movies);
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(USER_ID, null, null, 1, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalItems()).isEqualTo(3);
    }

    @Test
    void should_computeTotalPagesFromTotalItemsAndPageSize() {
        List<LibraryMovie> movies = List.of(
                movie(1L, 1L, "A", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-01T00:00:00Z")),
                movie(2L, 2L, "B", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z")),
                movie(3L, 3L, "C", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-03T00:00:00Z")));
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(movies);
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(USER_ID, null, null, 1, 2);

        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void should_returnTheSecondPageOfItems_when_pageIsTwo() {
        List<LibraryMovie> movies = List.of(
                movie(1L, 1L, "Newest", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-03T00:00:00Z")),
                movie(2L, 2L, "Middle", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-02T00:00:00Z")),
                movie(3L, 3L, "Oldest", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-01T00:00:00Z")));
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(movies);
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(USER_ID, null, null, 2, 2);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).title()).isEqualTo("Oldest");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(2);
    }

    @Test
    void should_defaultPageToOneAndPageSizeToTwenty_when_bothAreOmitted() {
        stubEmptyRepositories();

        LibraryListResponse response = libraryService.list(USER_ID, null, null, null, null);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
    }

    @Test
    void should_returnAnEmptyPage_when_theRequestedPageIsBeyondTheLastPage() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "Only Movie", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-01T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(USER_ID, null, null, 5, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isEqualTo(1);
    }

    // ---- AC-7: user scoping ----

    @Test
    void should_onlyQueryRepositoriesWithTheGivenUserId_when_listing() {
        stubEmptyRepositories();

        libraryService.list(USER_ID, null, null, null, null);

        verify(libraryMovieRepository).findByUserId(USER_ID);
        verify(librarySeriesRepository).findByUserId(USER_ID);
    }

    // ---- AC-8: empty results ----

    @Test
    void should_returnAnEmptyList_when_theUserHasNoLibraryItemsAtAll() {
        stubEmptyRepositories();

        LibraryListResponse response = libraryService.list(USER_ID, null, null, null, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void should_returnAnEmptyList_when_theFilterCombinationMatchesNothing() {
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(1L, 1L, "Planned Movie", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-11-01T00:00:00Z"))));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of());

        LibraryListResponse response = libraryService.list(
                USER_ID, null, List.of(WatchStatus.WATCHED), null, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalItems()).isZero();
    }

    // ---- sort order ----

    @Test
    void should_sortByAddedAtDescendingThenTypeAscendingThenIdAscending_when_buildingTheList() {
        Instant sameInstant = Instant.parse("2026-11-01T00:00:00Z");
        when(libraryMovieRepository.findByUserId(USER_ID)).thenReturn(List.of(
                movie(10L, 1L, "Older Movie", 2000, null, WatchStatus.PLANNED, Instant.parse("2026-10-01T00:00:00Z")),
                movie(2L, 2L, "Tied Movie High Id", 2000, null, WatchStatus.PLANNED, sameInstant),
                movie(1L, 3L, "Tied Movie Low Id", 2000, null, WatchStatus.PLANNED, sameInstant)));
        when(librarySeriesRepository.findByUserId(USER_ID)).thenReturn(List.of(
                series(1L, 4L, "Tied Series", 2000, null, sameInstant, WatchStatus.PLANNED),
                series(1L, 5L, "Newest Series", 2000, null, Instant.parse("2026-11-02T00:00:00Z"), WatchStatus.PLANNED)));

        LibraryListResponse response = libraryService.list(USER_ID, null, null, 1, 10);

        assertThat(response.items()).extracting(LibraryItemResponse::title).containsExactly(
                "Newest Series",
                "Tied Movie Low Id",
                "Tied Movie High Id",
                "Tied Series",
                "Older Movie");
    }
}
