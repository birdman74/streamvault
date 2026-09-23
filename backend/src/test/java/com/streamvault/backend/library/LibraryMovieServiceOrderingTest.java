package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.streamvault.backend.library.dto.LibraryMovieResponse;
import com.streamvault.backend.library.exception.InvalidWatchStatusException;
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbMovie;

/**
 * Dev-authored lower-level unit tests below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Covers two nuances Test's
 * {@code LibraryMovieServiceTest} does not pin explicitly:
 *
 * <ul>
 *   <li>an unsupported status is rejected (400) <em>before</em> the duplicate check, so a bad status
 *       on a movie already in the library still yields 400, never 409, and touches neither
 *       collaborator;</li>
 *   <li>the response is built from the <em>saved</em> entity - {@code id} and {@code addedAt} are
 *       carried through from persistence, and {@code status} is the {@code WatchStatus.name()} string
 *       rather than the enum.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class LibraryMovieServiceOrderingTest {

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

    @Test
    void should_rejectUnsupportedStatusBeforeTouchingRepositoryOrGateway_evenWhenAlreadyInLibrary() {
        assertThatThrownBy(() -> libraryMovieService.addMovie(1L, TMDB_ID, "GARBAGE"))
                .isInstanceOf(InvalidWatchStatusException.class);

        verifyNoInteractions(libraryMovieRepository, tmdbGateway);
    }

    @Test
    void should_buildResponseFromTheSavedEntity_carryingIdAndAddedAtAndStatusName() throws Exception {
        LibraryMovie persisted = new LibraryMovie(1L, TMDB_ID, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg", WatchStatus.CURRENTLY_WATCHING);
        Field idField = LibraryMovie.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(persisted, 123L);

        when(libraryMovieRepository.existsByUserIdAndTmdbId(1L, TMDB_ID)).thenReturn(false);
        when(tmdbGateway.movie(TMDB_ID)).thenReturn(new TmdbMovie(TMDB_ID, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg"));
        when(libraryMovieRepository.save(any(LibraryMovie.class))).thenReturn(persisted);

        LibraryMovieResponse response =
                libraryMovieService.addMovie(1L, TMDB_ID, "CURRENTLY_WATCHING");

        assertThat(response.id()).isEqualTo(123L);
        assertThat(response.addedAt()).isEqualTo(persisted.getAddedAt());
        assertThat(response.status()).isEqualTo("CURRENTLY_WATCHING");
        assertThat(response.addedAt()).isNotNull();
        assertThat(response.addedAt()).isBeforeOrEqualTo(Instant.now());
    }
}
