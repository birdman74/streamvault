package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.streamvault.backend.user.User;
import com.streamvault.backend.user.UserRepository;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. {@code LibraryMovieRepository},
 * {@code LibraryMovie}, and {@code WatchStatus} do not exist yet; this test is expected to fail to
 * compile until Dev implements them.
 *
 * <p>{@code @SpringBootTest} with H2 so the real Flyway schema and JPA mapping are exercised together
 * (this also proves the new {@code @Entity} matches {@code V5} under {@code ddl-auto=validate}).
 * Covers the cross-story repository invariant - new finder methods return "no match" without
 * throwing - plus a save/find round-trip carrying every field including {@code status} (AC-2), and
 * that one user's rows are invisible to a query for another user's id (AC-6).
 */
@SpringBootTest
class LibraryMovieRepositoryTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_movie_repository;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("tmdb.api-key", () -> "test-tmdb-key");
    }

    @Autowired
    private LibraryMovieRepository libraryMovieRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() {
        libraryMovieRepository.deleteAll();
        userA = userRepository.save(new User("library-repo-a@example.com", "bcrypt-hash")).getId();
        userB = userRepository.save(new User("library-repo-b@example.com", "bcrypt-hash")).getId();
    }

    @Test
    void should_returnEmptyOptional_when_noMovieMatchesUserAndTmdbId() {
        Optional<LibraryMovie> found = libraryMovieRepository.findByUserIdAndTmdbId(userA, 999L);

        assertThat(found).isEmpty();
    }

    @Test
    void should_returnFalse_when_existsIsCheckedForAMovieNotInLibrary() {
        assertThat(libraryMovieRepository.existsByUserIdAndTmdbId(userA, 999L)).isFalse();
    }

    @Test
    void should_roundTripALibraryMovie_when_savedThenLookedUpByUserAndTmdbId() {
        libraryMovieRepository.save(new LibraryMovie(userA, 27205L, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg", WatchStatus.CURRENTLY_WATCHING));

        Optional<LibraryMovie> found = libraryMovieRepository.findByUserIdAndTmdbId(userA, 27205L);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getUserId()).isEqualTo(userA);
        assertThat(found.get().getTmdbId()).isEqualTo(27205L);
        assertThat(found.get().getTitle()).isEqualTo("Inception");
        assertThat(found.get().getReleaseYear()).isEqualTo(2010);
        assertThat(found.get().getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");
        assertThat(found.get().getStatus()).isEqualTo(WatchStatus.CURRENTLY_WATCHING);
        assertThat(found.get().getAddedAt()).isNotNull();
    }

    @Test
    void should_notFindAnotherUsersMovie_when_queryingByUserId() {
        libraryMovieRepository.save(new LibraryMovie(userA, 27205L, "Inception", 2010, null,
                WatchStatus.PLANNED));

        assertThat(libraryMovieRepository.findByUserIdAndTmdbId(userB, 27205L)).isEmpty();
        assertThat(libraryMovieRepository.existsByUserIdAndTmdbId(userB, 27205L)).isFalse();
    }
}
