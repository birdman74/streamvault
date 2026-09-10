package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. The
 * {@code V5__create_library_movies_table.sql} migration does not exist yet; this test is expected to
 * fail until Dev adds it.
 *
 * <p>Mirrors {@code UserTableConstraintsTest}: {@code @SpringBootTest} with H2 in PostgreSQL mode so
 * Flyway runs the real migrations, then raw {@link JdbcTemplate} inserts to pin the schema-level
 * invariants that must hold no matter which application path writes the row:
 *
 * <ul>
 *   <li>{@code UNIQUE (user_id, tmdb_id)} - the database backstop for AC-4, while AC-5 (same id,
 *       different user) stays legal;</li>
 *   <li>{@code user_id} NOT NULL and a foreign key into {@code users} - a library row must belong to
 *       a real user (security-sensitive scoping column);</li>
 *   <li>{@code status} NOT NULL - the AC-3 default is applied in the app and a missing value is
 *       impossible at rest;</li>
 *   <li>{@code release_year} / {@code poster_url} accept NULL (AC-2: stored null when TMDB omits them).</li>
 * </ul>
 *
 * <p>{@code @Transactional} so each method's {@code @BeforeEach} user seed rolls back afterwards -
 * the class shares one cached context and one in-memory H2 database across methods, and the seeded
 * users use fixed emails, so without rollback the second method collides on {@code users.email}. The
 * schema-level violations under test are raised synchronously by H2 at statement execution, so the
 * surrounding rollback does not mask them.
 */
@SpringBootTest
@Transactional
class LibraryMoviesTableConstraintsTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_movies_constraints;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
        registry.add("tmdb.api-key", () -> "test-tmdb-key");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userA;
    private long userB;

    @BeforeEach
    void seedUsers() {
        userA = insertUser("library-constraints-a@example.com");
        userB = insertUser("library-constraints-b@example.com");
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, created_at) VALUES (?, ?, now())",
                email, "bcrypt-hash");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertLibraryMovie(Long userId, Long tmdbId, String status) {
        jdbcTemplate.update(
                "INSERT INTO library_movies (user_id, tmdb_id, title, release_year, poster_url, status, added_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, now())",
                userId, tmdbId, "Inception", 2010, "https://image.tmdb.org/t/p/w500/inception.jpg", status);
    }

    @Test
    void should_rejectSecondRow_when_sameUserAddsSameTmdbIdTwice() {
        insertLibraryMovie(userA, 27205L, "PLANNED");

        assertThatThrownBy(() -> insertLibraryMovie(userA, 27205L, "WATCHED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptRows_when_differentUsersAddTheSameTmdbId() {
        insertLibraryMovie(userA, 27205L, "PLANNED");
        insertLibraryMovie(userB, 27205L, "WATCHED");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_movies WHERE tmdb_id = ?", Integer.class, 27205L);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void should_rejectRow_when_userIdIsNull() {
        assertThatThrownBy(() -> insertLibraryMovie(null, 27205L, "PLANNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectRow_when_userIdReferencesNoUser() {
        assertThatThrownBy(() -> insertLibraryMovie(9_999_999L, 27205L, "PLANNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectRow_when_statusIsNull() {
        assertThatThrownBy(() -> insertLibraryMovie(userA, 27205L, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptRow_when_releaseYearAndPosterUrlAreNull() {
        jdbcTemplate.update(
                "INSERT INTO library_movies (user_id, tmdb_id, title, release_year, poster_url, status, added_at) "
                        + "VALUES (?, ?, ?, NULL, NULL, ?, now())",
                userA, 603L, "The Matrix", "PLANNED");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_movies WHERE user_id = ? AND tmdb_id = ?",
                Integer.class, userA, 603L);
        assertThat(count).isEqualTo(1);
    }
}
