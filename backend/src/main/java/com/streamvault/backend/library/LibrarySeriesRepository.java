package com.streamvault.backend.library;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every access is user-scoped: there is no unscoped finder, so no path can read across users (AC-7).
 * Both finder methods return "no match" without throwing ({@code false} / {@link Optional#empty()}),
 * the cross-story repository invariant. Saving the root cascades seasons and episodes in one
 * {@code save(...)} call; no separate season/episode repository is needed by this story.
 */
public interface LibrarySeriesRepository extends JpaRepository<LibrarySeries, Long> {

    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);

    Optional<LibrarySeries> findByUserIdAndTmdbId(Long userId, long tmdbId);
}
