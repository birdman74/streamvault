package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-008-agreed.md "Test Coverage Confirmation").
 * {@code LibrarySeriesRepositoryTest}'s round-trip pins that the cascade persists the whole
 * season/episode tree and that it reads back correctly; it does not itself assert that
 * {@code LibrarySeason#getId()} / {@code LibraryEpisode#getId()} come back populated. Those two
 * getters are otherwise only exercised by entity-wiring tests that never persist, so the
 * {@code @GeneratedValue(strategy = IDENTITY)} path on the two cascaded child tables
 * ({@code library_seasons}, {@code library_episodes}) is pinned here directly.
 */
@SpringBootTest
@Transactional
class LibrarySeriesCascadeIdentityTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_series_cascade_identity;MODE=PostgreSQL");
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

    private Long userId;

    @BeforeEach
    void setUp() {
        librarySeriesRepository.deleteAll();
        userId = userRepository.save(new User("library-series-cascade-identity@example.com", "bcrypt-hash")).getId();
    }

    @Test
    void should_assignGeneratedIds_when_seasonsAndEpisodesArePersistedViaCascade() {
        LibrarySeries series = new LibrarySeries(userId, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason season = new LibrarySeason(1);
        season.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.PLANNED));
        series.addSeason(season);

        LibrarySeries saved = librarySeriesRepository.save(series);

        LibrarySeason savedSeason = saved.getSeasons().get(0);
        LibraryEpisode savedEpisode = savedSeason.getEpisodes().get(0);
        assertThat(savedSeason.getId()).isNotNull();
        assertThat(savedEpisode.getId()).isNotNull();
    }
}
