package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.streamvault.backend.library.dto.LibraryMovieResponse;
import com.streamvault.backend.library.exception.DuplicateLibraryMovieException;
import com.streamvault.backend.library.exception.InvalidWatchStatusException;
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbMovie;
import com.streamvault.backend.tmdb.exception.TmdbTitleNotFoundException;
import com.streamvault.backend.tmdb.exception.TmdbUnavailableException;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. None of {@code LibraryMovieService},
 * {@code LibraryMovieRepository}, {@code LibraryMovie}, {@code WatchStatus},
 * {@code LibraryMovieResponse}, {@code DuplicateLibraryMovieException},
 * {@code InvalidWatchStatusException}, {@code TmdbGateway#movie}, {@code TmdbMovie}, or
 * {@code TmdbTitleNotFoundException} exists yet; this test is expected to fail to compile until Dev
 * implements them.
 *
 * <p>Mockito, mocks the repository and the TMDB gateway seam, mirrors {@code AccountSettingsServiceTest}.
 * This is the home for the add algorithm: status default and rejection (AC-3), duplicate detected
 * before TMDB is called (AC-4), per-user independence (AC-5), binding the row to the caller (AC-6),
 * and propagation of the not-found (AC-7) and unavailable errors with no write.
 */
@ExtendWith(MockitoExtension.class)
class LibraryMovieServiceTest {

    private static final long TMDB_ID = 27205L;

    @Mock
    private LibraryMovieRepository libraryMovieRepository;

    @Mock
    private TmdbGateway tmdbGateway;

    private LibraryMovieService libraryMovieService;

    @BeforeEach
    void setUp() {
        libraryMovieService = new LibraryMovieService(libraryMovieRepository, tmdbGateway);
    }

    private TmdbMovie inception() {
        return new TmdbMovie(TMDB_ID, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg");
    }

    private void stubSaveReturnsArgument() {
        when(libraryMovieRepository.save(any(LibraryMovie.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void should_persistTmdbCatalogDataAndReturnIt_when_movieIsAddedByAuthenticatedUser() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        stubSaveReturnsArgument();

        LibraryMovieResponse response = libraryMovieService.addMovie(1L, TMDB_ID, "PLANNED");

        assertThat(response.tmdbId()).isEqualTo(TMDB_ID);
        assertThat(response.title()).isEqualTo("Inception");
        assertThat(response.releaseYear()).isEqualTo(2010);
        assertThat(response.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");
        assertThat(response.status()).isEqualTo("PLANNED");

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(1L);
        assertThat(saved.getValue().getTmdbId()).isEqualTo(TMDB_ID);
        assertThat(saved.getValue().getTitle()).isEqualTo("Inception");
        assertThat(saved.getValue().getReleaseYear()).isEqualTo(2010);
        assertThat(saved.getValue().getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");
        assertThat(saved.getValue().getStatus()).isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_defaultStatusToPlanned_when_noStatusIsProvided() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        stubSaveReturnsArgument();

        LibraryMovieResponse response = libraryMovieService.addMovie(1L, TMDB_ID, null);

        assertThat(response.status()).isEqualTo("PLANNED");

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(WatchStatus.PLANNED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLANNED", "CURRENTLY_WATCHING", "WATCHED"})
    void should_storeChosenStatus_when_statusIsProvided(String status) {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        stubSaveReturnsArgument();

        LibraryMovieResponse response = libraryMovieService.addMovie(1L, TMDB_ID, status);

        assertThat(response.status()).isEqualTo(status);

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(WatchStatus.valueOf(status));
    }

    @Test
    void should_throwInvalidWatchStatusException_when_statusIsNotARecognizedValue() {
        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "SOON"))
                .isInstanceOf(InvalidWatchStatusException.class);

        verifyNoInteractions(tmdbGateway, libraryMovieRepository);
    }

    @Test
    void should_throwInvalidWatchStatusException_when_statusDiffersOnlyInCase() {
        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "planned"))
                .isInstanceOf(InvalidWatchStatusException.class);

        verifyNoInteractions(tmdbGateway, libraryMovieRepository);
    }

    @Test
    void should_throwDuplicateLibraryMovieException_when_userAlreadyHasThatMovie() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(true);

        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "PLANNED"))
                .isInstanceOf(DuplicateLibraryMovieException.class);

        verify(libraryMovieRepository, never()).save(any(LibraryMovie.class));
    }

    @Test
    void should_checkForDuplicateBeforeCallingTmdb_when_addingAMovie() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(true);

        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, null))
                .isInstanceOf(DuplicateLibraryMovieException.class);

        verifyNoInteractions(tmdbGateway);
    }

    @Test
    void should_translateUniqueConstraintViolationToDuplicateError_when_saveRaces() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        when(libraryMovieRepository.save(any(LibraryMovie.class)))
                .thenThrow(new DataIntegrityViolationException("uq_library_movies_user_tmdb"));

        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "PLANNED"))
                .isInstanceOf(DuplicateLibraryMovieException.class);
    }

    @Test
    void should_addIndependentlyForEachUser_when_twoUsersAddTheSameTmdbMovie() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(libraryMovieRepository.existsByUserIdAndTmdbId(2L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        stubSaveReturnsArgument();

        libraryMovieService.addMovie(1L, TMDB_ID, null);
        libraryMovieService.addMovie(2L, TMDB_ID, "WATCHED");

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getUserId()).isEqualTo(1L);
        assertThat(saved.getAllValues().get(0).getStatus()).isEqualTo(WatchStatus.PLANNED);
        assertThat(saved.getAllValues().get(1).getUserId()).isEqualTo(2L);
        assertThat(saved.getAllValues().get(1).getStatus()).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void should_bindTheNewRowToTheCallingUserId_when_movieIsAdded() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(99L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(inception());
        stubSaveReturnsArgument();

        libraryMovieService.addMovie(99L, TMDB_ID, null);

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(99L);
        verify(libraryMovieRepository).existsByUserIdAndTmdbId(99L, TMDB_ID);
    }

    @Test
    void should_propagateTmdbTitleNotFound_when_tmdbDoesNotRecognizeTheId() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, 999999L)).thenReturn(false);
        when(tmdbGateway.movie(999999L)).thenThrow(new TmdbTitleNotFoundException(999999L));

        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, 999999L, "PLANNED"))
                .isInstanceOf(TmdbTitleNotFoundException.class);

        verify(libraryMovieRepository, never()).save(any(LibraryMovie.class));
    }

    @Test
    void should_propagateTmdbUnavailable_when_tmdbCallFails() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenThrow(new TmdbUnavailableException("upstream 503"));

        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "PLANNED"))
                .isInstanceOf(TmdbUnavailableException.class);

        verify(libraryMovieRepository, never()).save(any(LibraryMovie.class));
    }

    @Test
    void should_storeNullReleaseYearAndPoster_when_tmdbOmitsThem() {
        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(new TmdbMovie(TMDB_ID, "Bare Movie", null, null));
        stubSaveReturnsArgument();

        LibraryMovieResponse response = libraryMovieService.addMovie(1L, TMDB_ID, null);

        assertThat(response.releaseYear()).isNull();
        assertThat(response.posterUrl()).isNull();

        ArgumentCaptor<LibraryMovie> saved = ArgumentCaptor.forClass(LibraryMovie.class);
        verify(libraryMovieRepository).save(saved.capture());
        assertThat(saved.getValue().getReleaseYear()).isNull();
        assertThat(saved.getValue().getPosterUrl()).isNull();
    }
}
