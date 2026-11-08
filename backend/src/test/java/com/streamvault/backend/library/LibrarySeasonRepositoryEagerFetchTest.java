package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md, design decision 8 (added round
 * 2, story-009-dev-feedback-r2.md, Concern 1). {@code LibrarySeasonRepository} is new: no prior
 * story needed one, since saving a {@code LibrarySeries} root cascades its seasons and episodes in
 * one {@code save(...)} call.
 *
 * <p>Proves, in isolation, that {@code findSeasonsWithEpisodesByLibrarySeriesIdIn}'s
 * {@code LEFT JOIN FETCH s.episodes} initializes {@code episodes} on every returned
 * {@code LibrarySeason} with no open session, under this project's actual
 * {@code spring.jpa.open-in-view: false}. This is the second half of the round 2 fetch split that
 * replaced the single two-level {@code @EntityGraph} round 1 proposed, which threw
 * {@code MultipleBagFetchException} against this project's actual bag-typed collections.
 *
 * <p>Deliberately NOT {@code @Transactional} at the class or method level, same discipline as
 * {@code LibrarySeriesRepositoryEagerFetchTest}: that would keep the Hibernate session open for the
 * whole test and silently hide the exact bug this test exists to catch. Each repository call below
 * runs in its own self-contained transaction, and the test cleans up for itself in
 * {@code @AfterEach} instead of relying on test-transaction rollback.
 *
 * <p>This test calls {@code librarySeriesRepository.save(...)} and then
 * {@code librarySeasonRepository.findSeasonsWithEpisodesByLibrarySeriesIdIn(...)} as two
 * independent top-level calls, deliberately - that isolates this query's own fetch behavior from
 * whether it shares a session with a separately-loaded {@code LibrarySeries.getSeasons()}.
 * Proving the two calls share one session when both happen inside
 * {@code LibraryService.list(...)} is {@code LibraryServiceEagerFetchIntegrationTest}'s job, not
 * this test's.
 */
@SpringBootTest
class LibrarySeasonRepositoryEagerFetchTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:library_season_repository_eager_fetch;MODE=PostgreSQL");
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
    private LibrarySeasonRepository librarySeasonRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        librarySeriesRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void should_haveEpisodesAlreadyInitialized_when_accessedAfterFindSeasonsWithEpisodesReturns() {
        Long userId = userRepository
                .save(new User("library-season-eager-fetch@example.com", "bcrypt-hash"))
                .getId();

        LibrarySeries series = new LibrarySeries(userId, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason season1 = new LibrarySeason(1);
        season1.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.PLANNED));
        season1.addEpisode(new LibraryEpisode(2, "The Kingsroad", WatchStatus.PLANNED));
        series.addSeason(season1);
        Long seriesId = librarySeriesRepository.save(series).getId();

        List<LibrarySeason> found = librarySeasonRepository
                .findSeasonsWithEpisodesByLibrarySeriesIdIn(List.of(seriesId));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getEpisodes()).hasSize(2);
        assertThat(found.get(0).getEpisodes().get(0).getTitle()).isEqualTo("Winter Is Coming");
    }
}
