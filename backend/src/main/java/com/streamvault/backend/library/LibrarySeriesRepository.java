package com.streamvault.backend.library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every access is user-scoped: there is no unscoped finder, so no path can read across users (AC-7).
 * Every finder method returns "no match" without throwing ({@code false} / {@link Optional#empty()}
 * / {@link List#of()}), the cross-story repository invariant. Saving the root cascades seasons and
 * episodes in one {@code save(...)} call; no separate season repository is needed for writes.
 *
 * <p>{@code findByUserId}'s {@code @EntityGraph} fetches only {@code seasons}, not
 * {@code seasons.episodes}: a two-level bag fetch here throws
 * {@code MultipleBagFetchException} since both collections are {@code List}-typed
 * {@code @OneToMany}s (story-009-api-contracts.md, design decision 8). Episode initialization is
 * {@link LibrarySeasonRepository}'s concern, run inside the same transaction by
 * {@code LibraryService.list(...)}.
 */
public interface LibrarySeriesRepository extends JpaRepository<LibrarySeries, Long> {

    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);

    Optional<LibrarySeries> findByUserIdAndTmdbId(Long userId, long tmdbId);

    @EntityGraph(attributePaths = {"seasons"})
    List<LibrarySeries> findByUserId(Long userId);
}
