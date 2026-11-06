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
 * Contract lives in docs/specs/design/story-009-api-contracts.md, design decision 8. Added in
 * response to Dev's round 1 feedback (story-009-dev-feedback-r1.md, Concerns 1 and 2):
 * {@code LibrarySeriesRepository}, {@code LibrarySeries}, {@code LibrarySeason}, and
 * {@code LibraryEpisode} already exist from story-008, but this test's assertions are expected to
 * fail (not fail to compile) until Dev adds the {@code @EntityGraph} this contract now requires on
 * {@code findByUserId}.
 *
 * <p>With {@code spring.jpa.open-in-view: false} (this project's actual {@code application.yml})
 * and no outer transaction, the Hibernate session used by {@code findByUserId} closes the moment
 * the call returns. {@code seasons} must already be initialized by then, or every caller that
 * walks into it afterwards throws {@code LazyInitializationException}.
 *
 * <p>Revised round 2 (story-009-dev-feedback-r2.md, Concern 1): this test originally also asserted
 * that {@code seasons.episodes} was initialized by this same call, under a two-level
 * {@code @EntityGraph(attributePaths = {"seasons", "seasons.episodes"})}. Dev found that two nested
 * bag paths in one entity graph throws {@code MultipleBagFetchException} against this project's
 * actual mappings ({@code LibrarySeries.seasons} / {@code LibrarySeason.episodes} are both
 * {@code List}-typed, i.e. Hibernate bags). {@code findByUserId} now only fetches {@code seasons};
 * episode initialization is a separate repository call proved by
 * {@code LibrarySeasonRepositoryEagerFetchTest}, and the two calls sharing one session when made
 * from inside {@code LibraryService.list(...)} is proved by
 * {@code LibraryServiceEagerFetchIntegrationTest}.
 *
 * <p>Deliberately NOT {@code @Transactional} at the class or method level: that would keep the
 * Hibernate session open for the whole test and silently hide the exact bug this test exists to
 * catch. Each repository call below therefore runs in its own self-contained transaction exactly as
 * it would on the real {@code GET /api/library} request path, and the test cleans up for itself in
 * {@code @AfterEach} instead of relying on test-transaction rollback.
 */
@SpringBootTest
class LibrarySeriesRepositoryEagerFetchTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:library_series_repository_eager_fetch;MODE=PostgreSQL");
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

    @AfterEach
    void cleanUp() {
        librarySeriesRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void should_haveSeasonsAlreadyInitialized_when_accessedAfterFindByUserIdReturns() {
        Long userId = userRepository
                .save(new User("library-series-eager-fetch@example.com", "bcrypt-hash"))
                .getId();

        LibrarySeries series = new LibrarySeries(userId, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason season0 = new LibrarySeason(0);
        season0.addEpisode(new LibraryEpisode(1, "Series Recap", WatchStatus.PLANNED));
        LibrarySeason season1 = new LibrarySeason(1);
        season1.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.PLANNED));
        season1.addEpisode(new LibraryEpisode(2, "The Kingsroad", WatchStatus.PLANNED));
        series.addSeason(season0);
        series.addSeason(season1);
        librarySeriesRepository.save(series);

        List<LibrarySeries> found = librarySeriesRepository.findByUserId(userId);

        LibrarySeries loaded = found.get(0);
        assertThat(loaded.getSeasons()).hasSize(2);
        assertThat(loaded.getSeasons().get(1).getSeasonNumber()).isEqualTo(1);
    }
}
