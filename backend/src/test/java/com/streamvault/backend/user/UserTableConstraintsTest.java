package com.streamvault.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Gap identified in Brian's PR #4 review (User.java:23): the V2 migration dropped the
 * password_hash NOT NULL constraint so Google-linked users can be created without one, but
 * nothing at the database level stops a row from ending up with BOTH password_hash and google_id
 * null — an account nobody could ever authenticate into. A plain NOT NULL on password_hash isn't
 * the fix (it would break Google sign-in), so this pins the actual requirement: a row must always
 * have at least one of the two, enforced at the schema level so it holds regardless of which
 * application code path writes the row.
 */
@SpringBootTest
class UserTableConstraintsTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:user_table_constraints;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("app.jwt.secret", () -> "test-secret-key-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.expiration-ms", () -> "86400000");
        registry.add("app.google.client-id", () -> "test-client-id.apps.googleusercontent.com");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_rejectRow_when_bothPasswordHashAndGoogleIdAreNull() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, google_id, created_at) VALUES (?, NULL, NULL, now())",
                "no-credentials@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_acceptRow_when_googleIdIsSetAndPasswordHashIsNull() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, google_id, created_at) VALUES (?, NULL, ?, now())",
                "google-only@example.com", "google-subject-1");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "google-only@example.com");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void should_acceptRow_when_passwordHashIsSetAndGoogleIdIsNull() {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, google_id, created_at) VALUES (?, ?, NULL, now())",
                "local-only@example.com", "bcrypt-hash");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, "local-only@example.com");
        assertThat(count).isEqualTo(1);
    }
}
