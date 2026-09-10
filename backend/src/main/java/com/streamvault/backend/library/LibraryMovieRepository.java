package com.streamvault.backend.library;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every access is user-scoped: there is no unscoped finder, so no path can read across users (AC-6).
 * Both finder methods return "no match" without throwing ({@code false} / {@link Optional#empty()}),
 * the cross-story repository invariant.
 */
public interface LibraryMovieRepository extends JpaRepository<LibraryMovie, Long> {

    boolean existsByUserIdAndTmdbId(Long userId, long tmdbId);

    Optional<LibraryMovie> findByUserIdAndTmdbId(Long userId, long tmdbId);
}
