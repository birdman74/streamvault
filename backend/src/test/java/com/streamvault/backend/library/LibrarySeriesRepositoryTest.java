package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-008-api-contracts.md. {@code LibrarySeriesRepository},
 * {@code LibrarySeries}, {@code LibrarySeason}, and {@code LibraryEpisode} do not exist yet; this
 * test is expected to fail to compile until Dev implements them.
 *
 * <p>{@code @SpringBootTest} with H2 so the real Flyway schema and JPA mapping are exercised
 * together (this also proves the three new {@code @Entity} classes match {@code V6} under
 * {@code ddl-auto=validate}). Covers the cross-story repository invariant - new finder methods
 * return "no match" without throwing - plus a save/find round-trip carrying the full season/episode
 * tree built via the entity's {@code addSeason} / {@code addEpisode} helpers and persisted with one
 * cascading {@code save} (AC-2), and that one user's rows are invisible to a query for another
 * user's id (AC-7).
 *
 * <p>{@code @Transactional} so each method's {@code @BeforeEach} user seed rolls back afterwards -
 * the class shares one cached context and one in-memory H2 database across methods, and the seeded
 * users use fixed emails, so without rollback the second method collides on {@code users.email}.
 */
@SpringBootTest
@Transactional
class LibrarySeriesRepositoryTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_series_repository;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("tmdb.api-key", () -> "test-tmdb-key");
    }

    @Autowired
    private LibrarySeriesRepository librarySeriesRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() {
        librarySeriesRepository.deleteAll();
        userA = userRepository.save(new User("library-series-repo-a@example.com", "bcrypt-hash")).getId();
        userB = userRepository.save(new User("library-series-repo-b@example.com", "bcrypt-hash")).getId();
    }

    private LibrarySeries gameOfThrones(Long userId) {
        LibrarySeries series = new LibrarySeries(userId, 1399L, "Game of Thrones", 2011,
                "https://image.tmdb.org/t/p/w500/got.jpg");
        LibrarySeason season0 = new LibrarySeason(0);
        season0.addEpisode(new LibraryEpisode(1, "Series Recap", WatchStatus.PLANNED));
        LibrarySeason season1 = new LibrarySeason(1);
        season1.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.PLANNED));
        season1.addEpisode(new LibraryEpisode(2, "The Kingsroad", WatchStatus.PLANNED));
        series.addSeason(season0);
        series.addSeason(season1);
        return series;
    }

    @Test
    void should_returnEmptyOptional_when_noSeriesMatchesUserAndTmdbId() {
        Optional<LibrarySeries> found = librarySeriesRepository.findByUserIdAndTmdbId(userA, 999L);

        assertThat(found).isEmpty();
    }

    @Test
    void should_returnFalse_when_existsIsCheckedForASeriesNotInLibrary() {
        assertThat(librarySeriesRepository.existsByUserIdAndTmdbId(userA, 999L)).isFalse();
    }

    @Test
    void should_roundTripASeriesWithSeasonsAndEpisodes_when_savedThenLookedUpByUserAndTmdbId() {
        librarySeriesRepository.save(gameOfThrones(userA));

        Optional<LibrarySeries> found = librarySeriesRepository.findByUserIdAndTmdbId(userA, 1399L);

        assertThat(found).isPresent();
        LibrarySeries series = found.get();
        assertThat(series.getId()).isNotNull();
        assertThat(series.getUserId()).isEqualTo(userA);
        assertThat(series.getTmdbId()).isEqualTo(1399L);
        assertThat(series.getTitle()).isEqualTo("Game of Thrones");
        assertThat(series.getFirstAirYear()).isEqualTo(2011);
        assertThat(series.getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/got.jpg");
        assertThat(series.getAddedAt()).isNotNull();
        assertThat(series.getSeasons()).hasSize(2);
        assertThat(series.getSeasons().get(0).getSeasonNumber()).isEqualTo(0);
        assertThat(series.getSeasons().get(0).getEpisodes()).hasSize(1);
        assertThat(series.getSeasons().get(1).getSeasonNumber()).isEqualTo(1);
        assertThat(series.getSeasons().get(1).getEpisodes()).hasSize(2);
        assertThat(series.getSeasons().get(1).getEpisodes().get(0).getTitle()).isEqualTo("Winter Is Coming");
        assertThat(series.getSeasons().get(1).getEpisodes().get(0).getStatus()).isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_notFindAnotherUsersSeries_when_queryingByUserId() {
        librarySeriesRepository.save(gameOfThrones(userA));

        assertThat(librarySeriesRepository.findByUserIdAndTmdbId(userB, 1399L)).isEmpty();
        assertThat(librarySeriesRepository.existsByUserIdAndTmdbId(userB, 1399L)).isFalse();
    }
}
