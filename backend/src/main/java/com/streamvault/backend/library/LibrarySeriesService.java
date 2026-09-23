package com.streamvault.backend.library;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.streamvault.backend.library.dto.LibraryEpisodeResponse;
import com.streamvault.backend.library.dto.LibrarySeasonResponse;
import com.streamvault.backend.library.dto.LibrarySeriesResponse;
import com.streamvault.backend.library.exception.DuplicateLibrarySeriesException;
import com.streamvault.backend.tmdb.TmdbGateway;
import com.streamvault.backend.tmdb.dto.TmdbEpisode;
import com.streamvault.backend.tmdb.dto.TmdbSeason;
import com.streamvault.backend.tmdb.dto.TmdbSeries;

/**
 * Owns the add algorithm. Depends only on the repository and the TMDB gateway (constructor
 * injection) - no {@code UserRepository}, no cross-user access; the owning user id is always the one
 * the controller resolved from the JWT principal (AC-7).
 *
 * <p>Order of operations (each step's failure leaves nothing written):
 * <ol>
 *   <li>{@code existsByUserIdAndTmdbId} -&gt; {@link DuplicateLibrarySeriesException}, checked
 *       <em>before</em> TMDB so a re-add costs no upstream call (AC-5).</li>
 *   <li>{@code tmdbGateway.series(tmdbId)} -&gt; propagates {@code TmdbSeriesNotFoundException}
 *       (AC-8) or {@code TmdbUnavailableException} unchanged, with no save.</li>
 *   <li>Build the aggregate via {@code addSeason} / {@code addEpisode}, forcing every episode to
 *       {@link WatchStatus#PLANNED} regardless of anything TMDB sends (AC-4), including season
 *       {@code 0} as its own grouping (AC-9).</li>
 *   <li>{@code repository.save(...)}, cascading the whole tree; a
 *       {@link DataIntegrityViolationException} from the {@code (user_id, tmdb_id)} unique
 *       constraint is translated to {@link DuplicateLibrarySeriesException} (race backstop).</li>
 *   <li>Map the saved aggregate to {@link LibrarySeriesResponse}.</li>
 * </ol>
 */
@Service
public class LibrarySeriesService {

    private final LibrarySeriesRepository librarySeriesRepository;
    private final TmdbGateway tmdbGateway;

    public LibrarySeriesService(LibrarySeriesRepository librarySeriesRepository, TmdbGateway tmdbGateway) {
        this.librarySeriesRepository = librarySeriesRepository;
        this.tmdbGateway = tmdbGateway;
    }

    public LibrarySeriesResponse addSeries(Long userId, Long tmdbId) {
        if (librarySeriesRepository.existsByUserIdAndTmdbId(userId, tmdbId)) {
            throw new DuplicateLibrarySeriesException();
        }

        TmdbSeries series = tmdbGateway.series(tmdbId);

        LibrarySeries entity = new LibrarySeries(userId, tmdbId, series.title(),
                series.firstAirYear(), series.posterUrl());
        for (TmdbSeason season : series.seasons()) {
            LibrarySeason seasonEntity = new LibrarySeason(season.seasonNumber());
            for (TmdbEpisode episode : season.episodes()) {
                seasonEntity.addEpisode(
                        new LibraryEpisode(episode.episodeNumber(), episode.title(), WatchStatus.PLANNED));
            }
            entity.addSeason(seasonEntity);
        }

        LibrarySeries saved;
        try {
            saved = librarySeriesRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateLibrarySeriesException();
        }

        return toResponse(saved);
    }

    private LibrarySeriesResponse toResponse(LibrarySeries series) {
        List<LibrarySeasonResponse> seasons = series.getSeasons().stream()
                .map(this::toSeasonResponse)
                .toList();
        return new LibrarySeriesResponse(
                series.getId(),
                series.getTmdbId(),
                series.getTitle(),
                series.getFirstAirYear(),
                series.getPosterUrl(),
                series.getAddedAt(),
                seasons);
    }

    private LibrarySeasonResponse toSeasonResponse(LibrarySeason season) {
        List<LibraryEpisodeResponse> episodes = season.getEpisodes().stream()
                .map(episode -> new LibraryEpisodeResponse(
                        episode.getEpisodeNumber(), episode.getTitle(), episode.getStatus().name()))
                .toList();
        return new LibrarySeasonResponse(season.getSeasonNumber(), episodes);
    }
}
