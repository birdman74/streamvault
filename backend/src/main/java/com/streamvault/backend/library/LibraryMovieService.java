package com.streamvault.backend.library;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.streamvault.backend.library.dto.LibraryMovieResponse;
import com.streamvault.backend.library.exception.DuplicateLibraryMovieException;
import com.streamvault.backend.library.exception.InvalidWatchStatusException;
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbMovie;

/**
 * Owns the add algorithm. Depends only on the repository and the TMDB gateway (constructor
 * injection) - no {@code UserRepository}, no cross-user access; the owning user id is always the one
 * the controller resolved from the JWT principal (AC-6).
 *
 * <p>Order of operations (each step's failure leaves nothing written):
 * <ol>
 *   <li>Resolve the status: {@code null} -&gt; {@link WatchStatus#PLANNED}; otherwise
 *       {@code WatchStatus.valueOf} wrapped, throwing {@link InvalidWatchStatusException}
 *       (case-sensitive). No repository or gateway call on this path (AC-3).</li>
 *   <li>{@code existsByUserIdAndTmdbId} -&gt; {@link DuplicateLibraryMovieException}, checked
 *       <em>before</em> TMDB so a re-add costs no upstream call (AC-4).</li>
 *   <li>{@code tmdbGateway.movie(tmdbId)} -&gt; propagates {@code TmdbTitleNotFoundException} (AC-7)
 *       or {@code TmdbUnavailableException} unchanged, with no save.</li>
 *   <li>{@code repository.save(...)}; a {@link DataIntegrityViolationException} from the
 *       {@code (user_id, tmdb_id)} unique constraint is translated to
 *       {@link DuplicateLibraryMovieException} (race backstop).</li>
 *   <li>Map the saved row to {@link LibraryMovieResponse} ({@code status} is
 *       {@code WatchStatus.name()}).</li>
 * </ol>
 */
@Service
public class LibraryMovieService {

    private final LibraryMovieRepository libraryMovieRepository;
    private final TmdbGateway tmdbGateway;

    public LibraryMovieService(LibraryMovieRepository libraryMovieRepository, TmdbGateway tmdbGateway) {
        this.libraryMovieRepository = libraryMovieRepository;
        this.tmdbGateway = tmdbGateway;
    }

    public LibraryMovieResponse addMovie(Long userId, Long tmdbId, String statusValue) {
        WatchStatus status = parseWatchStatus(statusValue);

        if (libraryMovieRepository.existsByUserIdAndTmdbId(userId, tmdbId)) {
            throw new DuplicateLibraryMovieException();
        }

        TmdbMovie movie = tmdbGateway.movie(tmdbId);

        LibraryMovie entity = new LibraryMovie(userId, tmdbId, movie.title(),
                movie.releaseYear(), movie.posterUrl(), status);

        LibraryMovie saved;
        try {
            saved = libraryMovieRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateLibraryMovieException();
        }

        return toResponse(saved);
    }

    private WatchStatus parseWatchStatus(String value) {
        if (value == null) {
            return WatchStatus.PLANNED;
        }
        try {
            return WatchStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidWatchStatusException(value);
        }
    }

    private LibraryMovieResponse toResponse(LibraryMovie movie) {
        return new LibraryMovieResponse(
                movie.getId(),
                movie.getTmdbId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getPosterUrl(),
                movie.getStatus().name(),
                movie.getAddedAt());
    }
}
