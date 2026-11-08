package com.streamvault.backend.library;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamvault.backend.library.dto.LibraryItemResponse;
import com.streamvault.backend.library.dto.LibraryListResponse;

/**
 * Merges {@link LibraryMovieRepository} and {@link LibrarySeriesRepository} into one read-only,
 * filterable, paginated list for {@code GET /api/library} (AC-1, AC-6, AC-7). Filtering, sorting,
 * and pagination happen in memory over each user's own rows rather than a cross-table SQL query
 * (story-009-api-contracts.md, design decision 2) - a reasonable trade for a personal-use library
 * size.
 *
 * <p>{@code @Transactional(readOnly = true)} is mandatory, not just a hint: it is the mechanism
 * that lets {@code librarySeriesRepository.findByUserId} and
 * {@code librarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn} share one Hibernate
 * session (Spring's default {@code REQUIRED} propagation), so the second call's fetched
 * {@code episodes} collections attach onto the already-loaded {@code LibrarySeason} instances
 * from the first call by entity identity, with no {@code LazyInitializationException} once this
 * method returns under {@code spring.jpa.open-in-view: false} (design decision 8).
 */
@Service
public class LibraryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final Comparator<LibraryItemResponse> SORT_ORDER =
            Comparator.comparing(LibraryItemResponse::addedAt, Comparator.reverseOrder())
                    .thenComparing(LibraryItemResponse::type)
                    .thenComparing(LibraryItemResponse::id);

    private final LibraryMovieRepository libraryMovieRepository;
    private final LibrarySeriesRepository librarySeriesRepository;
    private final LibrarySeasonRepository librarySeasonRepository;

    public LibraryService(LibraryMovieRepository libraryMovieRepository,
            LibrarySeriesRepository librarySeriesRepository,
            LibrarySeasonRepository librarySeasonRepository) {
        this.libraryMovieRepository = libraryMovieRepository;
        this.librarySeriesRepository = librarySeriesRepository;
        this.librarySeasonRepository = librarySeasonRepository;
    }

    @Transactional(readOnly = true)
    public LibraryListResponse list(Long userId, List<LibraryItemType> types,
            List<WatchStatus> statuses, Integer page, Integer pageSize) {
        int effectivePage = page == null ? DEFAULT_PAGE : page;
        int effectivePageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        Set<LibraryItemType> effectiveTypes = types == null || types.isEmpty()
                ? EnumSet.allOf(LibraryItemType.class)
                : EnumSet.copyOf(types);
        Set<WatchStatus> effectiveStatuses = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(WatchStatus.class)
                : EnumSet.copyOf(statuses);

        Stream<LibraryItemResponse> movieItems = effectiveTypes.contains(LibraryItemType.MOVIE)
                ? libraryMovieRepository.findByUserId(userId).stream().map(this::toItem)
                : Stream.empty();
        Stream<LibraryItemResponse> seriesItems = effectiveTypes.contains(LibraryItemType.SERIES)
                ? seriesItems(userId)
                : Stream.empty();

        List<LibraryItemResponse> filtered = Stream.concat(movieItems, seriesItems)
                .filter(item -> effectiveStatuses.contains(WatchStatus.valueOf(item.status())))
                .sorted(SORT_ORDER)
                .toList();

        int totalItems = filtered.size();
        int totalPages = totalItems == 0 ? 0 : ceilDiv(totalItems, effectivePageSize);
        List<LibraryItemResponse> pageItems = slice(filtered, effectivePage, effectivePageSize);

        return new LibraryListResponse(effectivePage, effectivePageSize, totalItems, totalPages, pageItems);
    }

    private Stream<LibraryItemResponse> seriesItems(Long userId) {
        List<LibrarySeries> series = librarySeriesRepository.findByUserId(userId);
        if (series.isEmpty()) {
            return Stream.empty();
        }
        List<Long> seriesIds = series.stream().map(LibrarySeries::getId).toList();
        librarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn(seriesIds);
        return series.stream().map(this::toItem);
    }

    private List<LibraryItemResponse> slice(List<LibraryItemResponse> items, int page, int pageSize) {
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + pageSize, items.size());
        return items.subList(fromIndex, toIndex);
    }

    private int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private LibraryItemResponse toItem(LibraryMovie movie) {
        return new LibraryItemResponse(
                movie.getId(),
                LibraryItemType.MOVIE.name(),
                movie.getTmdbId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getPosterUrl(),
                movie.getStatus().name(),
                movie.getAddedAt());
    }

    private LibraryItemResponse toItem(LibrarySeries series) {
        return new LibraryItemResponse(
                series.getId(),
                LibraryItemType.SERIES.name(),
                series.getTmdbId(),
                series.getTitle(),
                series.getFirstAirYear(),
                series.getPosterUrl(),
                WatchStatusRollup.seriesStatus(series).name(),
                series.getAddedAt());
    }
}
