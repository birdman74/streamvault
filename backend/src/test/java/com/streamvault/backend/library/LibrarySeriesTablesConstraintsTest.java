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
 * Contract lives in docs/specs/design/story-008-api-contracts.md. The
 * {@code V6__create_library_series_tables.sql} migration does not exist yet; this test is expected
 * to fail until Dev adds it.
 *
 * <p>Mirrors story-007's {@code LibraryMoviesTableConstraintsTest}, extended across the three new
 * tables: {@code @SpringBootTest} with H2 in PostgreSQL mode so Flyway runs the real migrations,
 * then raw {@link JdbcTemplate} inserts (and one delete) to pin the schema-level invariants that
 * must hold no matter which application path writes the rows:
 *
 * <ul>
 *   <li>{@code UNIQUE (user_id, tmdb_id)} on {@code library_series} - the database backstop for
 *       AC-5, while AC-6 (same id, different user) stays legal;</li>
 *   <li>{@code UNIQUE (library_series_id, season_number)} and
 *       {@code UNIQUE (library_season_id, episode_number)} - a season/episode number can only
 *       appear once per parent (AC-2);</li>
 *   <li>every FK column ({@code library_series.user_id}, {@code library_seasons.library_series_id},
 *       {@code library_episodes.library_season_id}) is NOT NULL with a real foreign key;</li>
 *   <li>{@code library_episodes.status} NOT NULL - the AC-4 default is applied in the app and a
 *       missing value is impossible at rest;</li>
 *   <li>{@code first_air_year} / {@code poster_url} / episode {@code title} accept NULL;</li>
 *   <li>season {@code 0} is accepted like any other season number (AC-9);</li>
 *   <li>{@code ON DELETE CASCADE} removes seasons and episodes when their series row is deleted -
 *       no orphaned child rows are structurally possible.</li>
 * </ul>
 *
 * <p>{@code @Transactional} so each method's {@code @BeforeEach} user seed rolls back afterwards -
 * the class shares one cached context and one in-memory H2 database across methods, and the seeded
 * users use fixed emails, so without rollback the second method collides on {@code users.email}.
 * The schema-level violations under test are raised synchronously by H2 at statement execution, so
 * the surrounding rollback does not mask them.
 */
@SpringBootTest
@Transactional
class LibrarySeriesTablesConstraintsTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:library_series_constraints;MODE=PostgreSQL");
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
        userA = insertUser("library-series-constraints-a@example.com");
        userB = insertUser("library-series-constraints-b@example.com");
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, created_at) VALUES (?, ?, now())",
                email, "bcrypt-hash");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertSeries(Long userId, Long tmdbId) {
        jdbcTemplate.update(
                "INSERT INTO library_series (user_id, tmdb_id, title, first_air_year, poster_url, added_at) "
                        + "VALUES (?, ?, ?, ?, ?, now())",
                userId, tmdbId, "Game of Thrones", 2011, "https://image.tmdb.org/t/p/w500/got.jpg");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM library_series WHERE user_id = ? AND tmdb_id = ?",
                Long.class, userId, tmdbId);
    }

    private void insertSeason(Long seriesId, Integer seasonNumber) {
        jdbcTemplate.update(
                "INSERT INTO library_seasons (library_series_id, season_number) VALUES (?, ?)",
                seriesId, seasonNumber);
    }

    private long insertSeasonReturningId(Long seriesId, Integer seasonNumber) {
        insertSeason(seriesId, seasonNumber);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM library_seasons WHERE library_series_id = ? AND season_number = ?",
                Long.class, seriesId, seasonNumber);
    }

    private void insertEpisode(Long seasonId, Integer episodeNumber, String title, String status) {
        jdbcTemplate.update(
                "INSERT INTO library_episodes (library_season_id, episode_number, title, status) "
                        + "VALUES (?, ?, ?, ?)",
                seasonId, episodeNumber, title, status);
    }

    @Test
    void should_rejectSecondSeriesRow_when_sameUserAddsSameTmdbIdTwice() {
        insertSeries(userA, 1399L);

        assertThatThrownBy(() -> insertSeries(userA, 1399L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptSeriesRows_when_differentUsersAddTheSameTmdbId() {
        insertSeries(userA, 1399L);
        insertSeries(userB, 1399L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_series WHERE tmdb_id = ?", Integer.class, 1399L);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void should_rejectSeriesRow_when_userIdIsNull() {
        assertThatThrownBy(() -> insertSeries(null, 1399L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectSeriesRow_when_userIdReferencesNoUser() {
        assertThatThrownBy(() -> insertSeries(9_999_999L, 1399L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptSeriesRow_when_firstAirYearAndPosterUrlAreNull() {
        jdbcTemplate.update(
                "INSERT INTO library_series (user_id, tmdb_id, title, first_air_year, poster_url, added_at) "
                        + "VALUES (?, ?, ?, NULL, NULL, now())",
                userA, 603L, "Bare Series");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_series WHERE user_id = ? AND tmdb_id = ?",
                Integer.class, userA, 603L);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void should_rejectSeasonRow_when_seriesIdReferencesNoSeries() {
        assertThatThrownBy(() -> insertSeason(9_999_999L, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectSecondSeasonRow_when_sameSeriesHasSameSeasonNumberTwice() {
        long seriesId = insertSeries(userA, 1399L);
        insertSeason(seriesId, 1);

        assertThatThrownBy(() -> insertSeason(seriesId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptSeasonRow_when_seasonNumberIsZero() {
        long seriesId = insertSeries(userA, 1399L);

        insertSeason(seriesId, 0);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_seasons WHERE library_series_id = ? AND season_number = 0",
                Integer.class, seriesId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void should_rejectEpisodeRow_when_seasonIdReferencesNoSeason() {
        assertThatThrownBy(() -> insertEpisode(9_999_999L, 1, "Pilot", "PLANNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectSecondEpisodeRow_when_sameSeasonHasSameEpisodeNumberTwice() {
        long seriesId = insertSeries(userA, 1399L);
        long seasonId = insertSeasonReturningId(seriesId, 1);
        insertEpisode(seasonId, 1, "Winter Is Coming", "PLANNED");

        assertThatThrownBy(() -> insertEpisode(seasonId, 1, "Duplicate", "PLANNED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectEpisodeRow_when_statusIsNull() {
        long seriesId = insertSeries(userA, 1399L);
        long seasonId = insertSeasonReturningId(seriesId, 1);

        assertThatThrownBy(() -> insertEpisode(seasonId, 1, "Winter Is Coming", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptEpisodeRow_when_titleIsNull() {
        long seriesId = insertSeries(userA, 1399L);
        long seasonId = insertSeasonReturningId(seriesId, 1);

        insertEpisode(seasonId, 1, null, "PLANNED");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_episodes WHERE library_season_id = ? AND episode_number = 1",
                Integer.class, seasonId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void should_cascadeDeleteSeasonsAndEpisodes_when_aSeriesRowIsDeleted() {
        long seriesId = insertSeries(userA, 1399L);
        long seasonId = insertSeasonReturningId(seriesId, 1);
        insertEpisode(seasonId, 1, "Winter Is Coming", "PLANNED");

        jdbcTemplate.update("DELETE FROM library_series WHERE id = ?", seriesId);

        Integer seasonCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_seasons WHERE library_series_id = ?", Integer.class, seriesId);
        Integer episodeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_episodes WHERE library_season_id = ?", Integer.class, seasonId);
        assertThat(seasonCount).isZero();
        assertThat(episodeCount).isZero();
    }
}
