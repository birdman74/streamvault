package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-007-agreed.md "Test Coverage Confirmation"). Guards the immutability
 * assumption later stories rely on: the public constructor stamps a non-null {@code addedAt} and
 * leaves {@code id} null before persist, the {@code protected} no-arg constructor exists for JPA,
 * and the class exposes getters only (no setters).
 */
class LibraryMovieTest {

    @Test
    void should_stampAddedAtAndLeaveIdNull_when_constructedViaThePublicConstructor() {
        Instant before = Instant.now();

        LibraryMovie movie = new LibraryMovie(1L, 27205L, "Inception", 2010,
                "https://image.tmdb.org/t/p/w500/inception.jpg", WatchStatus.PLANNED);

        assertThat(movie.getId()).isNull();
        assertThat(movie.getAddedAt()).isNotNull();
        assertThat(movie.getAddedAt()).isAfterOrEqualTo(before);
        assertThat(movie.getUserId()).isEqualTo(1L);
        assertThat(movie.getTmdbId()).isEqualTo(27205L);
        assertThat(movie.getTitle()).isEqualTo("Inception");
        assertThat(movie.getReleaseYear()).isEqualTo(2010);
        assertThat(movie.getPosterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/inception.jpg");
        assertThat(movie.getStatus()).isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_acceptNullReleaseYearAndPoster_when_tmdbOmitsThem() {
        LibraryMovie movie = new LibraryMovie(1L, 603L, "The Matrix", null, null, WatchStatus.WATCHED);

        assertThat(movie.getReleaseYear()).isNull();
        assertThat(movie.getPosterUrl()).isNull();
    }

    @Test
    void should_haveAProtectedNoArgConstructor_forJpa() throws Exception {
        Constructor<LibraryMovie> constructor = LibraryMovie.class.getDeclaredConstructor();

        assertThat(Modifier.isProtected(constructor.getModifiers())).isTrue();
    }

    @Test
    void should_exposeGettersOnlyAndNoSetters() {
        assertThat(Arrays.stream(LibraryMovie.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set")))
                .isEmpty();
    }
}
