package com.streamvault.backend.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * New in story-009 (design decision 8, added round 2): no prior story needed this repository,
 * since saving a {@link LibrarySeries} root cascades its seasons and episodes in one
 * {@code save(...)} call. Exists solely to eager-fetch episodes for a batch of series in one
 * query, called from inside {@code LibraryService.list(...)}'s own transaction so the fetched
 * {@code episodes} collections attach onto the already-loaded {@link LibrarySeason} instances
 * from {@code LibrarySeriesRepository.findByUserId} by entity identity.
 *
 * <p>{@code DISTINCT} is required here (unlike {@code findByUserId}'s {@code @EntityGraph}
 * approach) because this is a hand-written {@code JOIN FETCH} query, not a derived method with an
 * entity graph - Spring Data JPA's Criteria-API execution path for {@code @EntityGraph}-annotated
 * derived methods de-duplicates the root entity automatically, but a hand-written JPQL
 * {@code JOIN FETCH} does not.
 */
public interface LibrarySeasonRepository extends JpaRepository<LibrarySeason, Long> {

    @Query("SELECT DISTINCT s FROM LibrarySeason s LEFT JOIN FETCH s.episodes "
            + "WHERE s.librarySeries.id IN :seriesIds")
    List<LibrarySeason> findSeasonsWithEpisodesByLibrarySeriesIdIn(
            @Param("seriesIds") List<Long> seriesIds);
}
