package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.streamvault.backend.library.dto.LibrarySeriesResponse;
import com.streamvault.backend.library.exception.DuplicateLibrarySeriesException;
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbEpisode;
import com.streamvault.backend.tmdb.dto.TmdbSeason;
import com.streamvault.backend.tmdb.dto.TmdbSeries;
import com.streamvault.backend.tmdb.exception.TmdbSeriesNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. None of {@code LibrarySeriesService},
 * {@code LibrarySeriesRepository}, {@code LibrarySeries}, {@code LibrarySeason}, {@code LibraryEpisode},
 * {@code LibrarySeriesResponse}, {@code DuplicateLibrarySeriesException}, {@code TmdbGateway#series},
 * {@code TmdbSeries}/{@code TmdbSeason}/{@code TmdbEpisode}, or {@code TmdbSeriesNotFoundException}
 * exists yet; this test is expected to fail to compile until Dev implements them.
 *
 * <p>Mockito, mocks the repository and the TMDB gateway seam, mirrors story-007's
 * {@code LibraryMovieServiceTest}. This is the home for the add algorithm: duplicate detected before
 * TMDB is called (AC-5), per-user independence (AC-6), binding the row to the caller (AC-7),
 * propagation of the not-found (AC-8) and unavailable errors with no write, the unique-constraint
 * race backstop, every episode forced to {@code PLANNED} regardless of anything TMDB sends (AC-4),
 * and season/episode tree fidelity including season {@code 0} (AC-9).
 */
@ExtendWith(MockitoExtension.class)
class LibrarySeriesServiceTest {

    private static final long TMDB_ID = 1399L;

    @Mock
    private LibrarySeriesRepository librarySeriesRepository;

    @Mock
    private TmdbGateway tmdbGateway;

    private LibrarySeriesService librarySeriesService;

    @BeforeEach
    void setUp() {
        librarySeriesService = new LibrarySeriesService(librarySeriesRepository, tmdbGateway);
    }

    private TmdbSeries gameOfThrones() {
        return new TmdbSeries(TMDB_ID, "Game of Thrones", 2011,
                "https://image.tmdb.org/t/p/w500/got.jpg",
                List.of(
                        new TmdbSeason(0, List.of(new TmdbEpisode(1, "Series Recap"))),
                        new TmdbSeason(1, List.of(
                                new TmdbEpisode(1, "Winter Is Coming"),
                                new TmdbEpisode(2, "The Kingsroad")))));
    }

    private void stubSaveReturnsArgument() {
        when(librarySeriesRepository.save(any(LibrarySeries.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void should_persistSeriesLevelCatalogDataAndFullStructure_when_seriesIsAddedByAuthenticatedUser() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        LibrarySeriesResponse response = librarySeriesService.addSeries(1L, TMDB_ID);

        assertThat(response.tmdbId()).isEqualTo(TMDB_ID);
        assertThat(response.title()).isEqualTo("Game of Thrones");
        assertThat(response.firstAirYear()).isEqualTo(2011);
        assertThat(response.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/got.jpg");

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getTmdbId()).isEqualTo(TMDB_ID);
        assertThat(saved.getValue().getTitle()).isEqualTo("Game of Thrones");
        assertThat(saved.getValue().getFirstAirYear()).isEqualTo(2011);
        assertThat(saved.getValue().getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/got.jpg");
    }

    @Test
    void should_storeEverySeasonAndEpisodeFromTmdb_when_seriesHasMultipleSeasons() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(1L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        List<LibrarySeason> seasons = saved.getValue().getSeasons();
        assertThat(seasons).hasSize(2);
        assertThat(seasons.get(0).getSeasonNumber()).isEqualTo(0);
        assertThat(seasons.get(0).getEpisodes()).hasSize(1);
        assertThat(seasons.get(1).getSeasonNumber()).isEqualTo(1);
        assertThat(seasons.get(1).getEpisodes()).hasSize(2);
        assertThat(seasons.get(1).getEpisodes().get(0).getEpisodeNumber()).isEqualTo(1);
        assertThat(seasons.get(1).getEpisodes().get(0).getTitle()).isEqualTo("Winter Is Coming");
        assertThat(seasons.get(1).getEpisodes().get(1).getEpisodeNumber()).isEqualTo(2);
        assertThat(seasons.get(1).getEpisodes().get(1).getTitle()).isEqualTo("The Kingsroad");
    }

    @Test
    void should_storeSeasonZeroAsItsOwnGrouping_when_tmdbIncludesSpecials() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(1L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        assertThat(saved.getValue().getSeasons().get(0).getSeasonNumber()).isZero();
        assertThat(saved.getValue().getSeasons().get(0).getEpisodes().get(0).getTitle())
                .isEqualTo("Series Recap");
    }

    @Test
    void should_setEveryEpisodeStatusToPlanned_when_seriesIsAdded() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(1L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        for (LibrarySeason season : saved.getValue().getSeasons()) {
            for (LibraryEpisode episode : season.getEpisodes()) {
                assertThat(episode.getStatus()).isEqualTo(WatchStatus.PLANNED);
            }
        }
    }

    @Test
    void should_throwDuplicateLibrarySeriesException_when_userAlreadyHasThatSeries() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(true);

        assertThatThrownBy(() -> librarySeriesService.addSeries(1L, TMDB_ID))
                .isInstanceOf(DuplicateLibrarySeriesException.class);

        verify(librarySeriesRepository, never()).save(any(LibrarySeries.class));
    }

    @Test
    void should_checkForDuplicateBeforeCallingTmdb_when_addingASeries() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(true);

        assertThatThrownBy(() -> librarySeriesService.addSeries(1L, TMDB_ID))
                .isInstanceOf(DuplicateLibrarySeriesException.class);

        verifyNoInteractions(tmdbGateway);
    }

    @Test
    void should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        when(librarySeriesRepository.save(any(LibrarySeries.class)))
                .thenThrow(new DataIntegrityViolationException("uq_library_series_user_tmdb"));

        assertThatThrownBy(() -> librarySeriesService.addSeries(1L, TMDB_ID))
                .isInstanceOf(DuplicateLibrarySeriesException.class);
    }

    @Test
    void should_addIndependentlyForEachUser_when_twoUsersAddTheSameTmdbSeries() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(librarySeriesRepository.existsByUserIdAndTmdbId(2L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(1L, TMDB_ID);
        librarySeriesService.addSeries(2L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getUserId()).isEqualTo(1L);
        assertThat(saved.getAllValues().get(1).getUserId()).isEqualTo(2L);
    }

    @Test
    void should_bindTheNewRowToTheCallingUserId_when_seriesIsAdded() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(99L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(gameOfThrones());
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(99L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(99L);
        verify(librarySeriesRepository).existsByUserIdAndTmdbId(99L, TMDB_ID);
    }

    @Test
    void should_propagateTmdbSeriesNotFound_when_tmdbDoesNotRecognizeTheId() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, 999999L)).thenReturn(false);
        when(tmdbGateway.series(999999L)).thenThrow(new TmdbSeriesNotFoundException(999999L));

        assertThatThrownBy(() -> librarySeriesService.addSeries(1L, 999999L))
                .isInstanceOf(TmdbSeriesNotFoundException.class);

        verify(librarySeriesRepository, never()).save(any(LibrarySeries.class));
    }

    @Test
    void should_propagateTmdbUnavailable_when_tmdbCallFails() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenThrow(new TmdbUnavailableException("upstream 503"));

        assertThatThrownBy(() -> librarySeriesService.addSeries(1L, TMDB_ID))
                .isInstanceOf(TmdbUnavailableException.class);

        verify(librarySeriesRepository, never()).save(any(LibrarySeries.class));
    }

    @Test
    void should_storeNullFirstAirYearAndPoster_when_tmdbOmitsThem() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(
                new TmdbSeries(TMDB_ID, "Bare Series", null, null, List.of()));
        stubSaveReturnsArgument();

        LibrarySeriesResponse response = librarySeriesService.addSeries(1L, TMDB_ID);

        assertThat(response.firstAirYear()).isNull();
        assertThat(response.posterUrl()).isNull();

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        assertThat(saved.getValue().getFirstAirYear()).isNull();
        assertThat(saved.getValue().getPosterUrl()).isNull();
    }

    @Test
    void should_storeNullEpisodeTitle_when_tmdbOmitsTheEpisodeName() {
        when(librarySeriesRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.series(TMDB_ID)).thenReturn(new TmdbSeries(TMDB_ID, "Untitled Episodes", 2020,
                null, List.of(new TmdbSeason(1, List.of(new TmdbEpisode(1, null))))));
        stubSaveReturnsArgument();

        librarySeriesService.addSeries(1L, TMDB_ID);

        ArgumentCaptor<LibrarySeries> saved = ArgumentCaptor.forClass(LibrarySeries.class);
        verify(librarySeriesRepository).save(saved.capture());
        assertThat(saved.getValue().getSeasons().get(0).getEpisodes().get(0).getTitle()).isNull();
    }
}
