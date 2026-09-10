package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards AC-7: story-006 is read-only against TMDB and writes nothing to any user's library.
 *
 * <p>Source/filesystem scanning, no Spring context -- mirrors {@code ControllerSliceTestAuthConventionTest}.
 * It reads the tree from disk so it is deterministic and holds no state between runs.
 *
 * <ol>
 *   <li>No class in the {@code com.streamvault.backend.tmdb} main package may reference a
 *       persistence API. The tokens checked are import/type level ({@code jakarta.persistence},
 *       {@code org.springframework.data}, {@code @Transactional}, {@code EntityManager},
 *       {@code JdbcTemplate}, {@code MongoTemplate}) so they do not false-match explanatory prose.</li>
 *   <li>The Flyway migration set is pinned to an explicit list, so a later edit cannot quietly add
 *       persistence under a TMDB-read feature. STORY-007 (Add Movie from TMDB to Library) legitimately
 *       adds {@code V5__create_library_movies_table.sql} for its own {@code library_movies} table;
 *       that one entry is added here as a documented cross-story amendment, not a silent change, and
 *       the guard otherwise still holds.</li>
 * </ol>
 *
 * Until Dev creates the {@code tmdb} main package this test fails on the first assertion, which is
 * the expected Phase 1 RED state.
 */
class TmdbPackageReadOnlyConventionTest {

    private static final Path TMDB_MAIN_PACKAGE =
            Path.of("src", "main", "java", "com", "streamvault", "backend", "tmdb");
    private static final Path MIGRATION_DIR =
            Path.of("src", "main", "resources", "db", "migration");

    private static final List<String> FORBIDDEN_PERSISTENCE_TOKENS = List.of(
            "jakarta.persistence",
            "org.springframework.data",
            "@Transactional",
            "EntityManager",
            "JdbcTemplate",
            "MongoTemplate");

    @Test
    void should_notReferenceAnyPersistenceApi_when_scanningTheTmdbMainSourcePackage() throws IOException {
        assertThat(Files.isDirectory(TMDB_MAIN_PACKAGE))
                .as("the TMDB feature package %s must exist (run from the backend module dir)",
                        TMDB_MAIN_PACKAGE.toAbsolutePath())
                .isTrue();

        List<String> offenders = javaSourcesUnder(TMDB_MAIN_PACKAGE)
                .filter(src -> FORBIDDEN_PERSISTENCE_TOKENS.stream().anyMatch(src.content()::contains))
                .map(JavaSource::relativePath)
                .sorted()
                .toList();

        assertThat(offenders)
                .as("story-006 is read-only against TMDB and writes nothing to any library (AC-7); "
                        + "no class in the tmdb package may reference a persistence API")
                .isEmpty();
    }

    @Test
    void should_notAddAnyNewFlywayMigration_when_theStoryIsImplemented() throws IOException {
        assertThat(Files.isDirectory(MIGRATION_DIR))
                .as("migration dir %s must exist", MIGRATION_DIR.toAbsolutePath())
                .isTrue();

        List<String> migrations;
        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            migrations = paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }

        assertThat(migrations)
                .as("the migration set is pinned; story-006 adds nothing, and STORY-007 adds only "
                        + "V5__create_library_movies_table.sql for its library_movies table "
                        + "(documented amendment, see class javadoc)")
                .containsExactly(
                        "V1__create_users_table.sql",
                        "V2__add_google_id_to_users.sql",
                        "V3__add_users_password_or_google_check.sql",
                        "V4__add_rating_type_to_users.sql",
                        "V5__create_library_movies_table.sql");
    }

    private Stream<JavaSource> javaSourcesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(JavaSource::read)
                    .toList()
                    .stream();
        }
    }

    private record JavaSource(Path path, String content) {

        static JavaSource read(Path path) {
            try {
                return new JavaSource(path, Files.readString(path));
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read source " + path, e);
            }
        }

        String relativePath() {
            return path.toString().replace('\\', '/');
        }
    }
}
