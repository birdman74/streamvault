package com.streamvault.backend.library;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One movie in one user's library. The first per-user table and the first foreign key into
 * {@code users}. Column shape mirrors the {@code users} table conventions: {@code IDENTITY} id,
 * {@code @Enumerated(STRING)} status into a {@code VARCHAR} column, and {@code added_at} mapped to an
 * {@link Instant} exactly as {@code User.createdAt} maps {@code users.created_at}. The unique
 * constraint {@code (user_id, tmdb_id)} lives in {@code V5__create_library_movies_table.sql}
 * (AC-4); the entity carries no {@code unique=true} of its own but must still match the columns so
 * {@code spring.jpa.hibernate.ddl-auto=validate} passes on a full context load.
 *
 * <p>Getters only, no setters: this story sets every field once at construction. The public
 * constructor stamps {@code addedAt}; the {@code protected} no-arg constructor exists for JPA.
 */
@Entity
@Table(name = "library_movies")
public class LibraryMovie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tmdb_id", nullable = false)
    private long tmdbId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WatchStatus status;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected LibraryMovie() {
    }

    public LibraryMovie(Long userId, long tmdbId, String title, Integer releaseYear,
            String posterUrl, WatchStatus status) {
        this.userId = userId;
        this.tmdbId = tmdbId;
        this.title = title;
        this.releaseYear = releaseYear;
        this.posterUrl = posterUrl;
        this.status = status;
        this.addedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public long getTmdbId() {
        return tmdbId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public WatchStatus getStatus() {
        return status;
    }

    public Instant getAddedAt() {
        return addedAt;
    }
}
