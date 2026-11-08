package com.streamvault.backend.library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every access is user-scoped: there is no unscoped finder, so no path can read across users (AC-6).
 * Every finder method returns "no match" without throwing ({@code false} / {@link Optional#empty()}
 * / {@link List#of()}), the cross-story repository invariant.
 */
public interface LibraryMovieRepository extends JpaRepository<LibraryMovie, Long> {

    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);

    Optional<LibraryMovie> findByUserIdAndTmdbId(Long userId, long tmdbId);

    List<LibraryMovie> findByUserId(Long userId);
}
