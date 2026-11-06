package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.streamvault.backend.library.dto.LibraryListResponse;
import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md, design decision 8 (added round
 * 2, story-009-dev-feedback-r2.md, Concern 1). Closes the gap that
 * {@code LibrarySeriesRepositoryEagerFetchTest} and {@code LibrarySeasonRepositoryEagerFetchTest}
 * cannot close on their own: each of those proves one query's own fetch behavior in isolation, by
 * calling it as an independent top-level call outside any already-open transaction. Neither proves
 * that {@code findByUserId} and {@code findSeasonsWithEpisodesByLibrarySeriesIdIn}, when both
 * called from inside the *same* {@code LibraryService.list(...)} invocation, actually share one
 * Hibernate session - which is what makes the episodes fetched by the second query attach, by
 * entity identity, onto the exact {@code LibrarySeason} instances the first query already loaded.
 * That sharing depends on Spring's default {@code REQUIRED} transaction propagation joining
 * {@code LibraryService.list(...)}'s own {@code @Transactional(readOnly = true)} boundary, which
 * only holds when both repository calls happen inside that one service method.
 *
 * <p>Uses the real, autowired {@code LibraryService} rather than Mockito mocks (unlike
 * {@code LibraryServiceTest}) because a mocked repository cannot exercise real Hibernate
 * session-sharing behavior - this test needs the real persistence context.
 *
 * <p>Deliberately NOT {@code @Transactional} at the class or method level, same discipline as the
 * two repository-level eager-fetch tests: that would keep the Hibernate session open for the whole
 * test (including the seed) and silently hide the exact bug this test exists to catch. The seed
 * commits in its own transaction; {@code libraryService.list(...)} then opens its own fresh
 * transaction exactly as it would on the real {@code GET /api/library} request path. Cleans up in
 * {@code @AfterEach} instead of relying on test-transaction rollback.
 *
 * <p>Asserts on the specific resulting status, not just "no exception": a silently-empty or
 * partially-initialized {@code episodes} collection would roll up to the wrong status
 * ({@code WATCHED} or {@code PLANNED}) rather than throwing, so only asserting "no exception
 * thrown" would not catch that failure mode.
 */
@SpringBootTest
class LibraryServiceEagerFetchIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:library_service_eager_fetch;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("tmdb.api-key", () -> "test-tmdb-key");
    }

    @Autowired
    private LibraryService libraryService;

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
    void should_rollUpASeriesStatusCorrectly_when_listIsCalledOnARealSpringContextAfterSeedingMultipleSeasons() {
        Long userId = userRepository
                .save(new User("library-service-eager-fetch@example.com", "bcrypt-hash"))
                .getId();

        LibrarySeries series = new LibrarySeries(userId, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason watchedSeason = new LibrarySeason(1);
        watchedSeason.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.WATCHED));
        watchedSeason.addEpisode(new LibraryEpisode(2, "The Kingsroad", WatchStatus.WATCHED));
        LibrarySeason plannedSeason = new LibrarySeason(2);
        plannedSeason.addEpisode(new LibraryEpisode(1, "The North Remembers", WatchStatus.PLANNED));
        series.addSeason(watchedSeason);
        series.addSeason(plannedSeason);
        librarySeriesRepository.save(series);

        LibraryListResponse response = libraryService.list(userId, null, null, null, null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo("CURRENTLY_WATCHING");
    }
}
