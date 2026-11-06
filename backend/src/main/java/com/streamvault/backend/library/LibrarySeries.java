package com.streamvault.backend.library;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One TV series in one user's library, the aggregate root over {@link LibrarySeason} /
 * {@link LibraryEpisode} (AC-2). The first multi-table aggregate in the {@code library} package:
 * saving the root cascades every season and episode in one call. {@link #addSeason(LibrarySeason)}
 * wires both sides of the relationship. Getters only, no setters: every field is set once at
 * construction. The public constructor stamps {@code addedAt}; the {@code protected} no-arg
 * constructor exists for JPA.
 */
@Entity
@Table(name = "library_series")
public class LibrarySeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tmdb_id", nullable = false)
    private long tmdbId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "first_air_year")
    private Integer firstAirYear;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @OneToMany(mappedBy = "librarySeries", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LibrarySeason> seasons = new ArrayList<>();

    protected LibrarySeries() {
    }

    public LibrarySeries(Long userId, long tmdbId, String title, Integer firstAirYear, String posterUrl) {
        this.userId = userId;
        this.tmdbId = tmdbId;
        this.title = title;
        this.firstAirYear = firstAirYear;
        this.posterUrl = posterUrl;
        this.addedAt = Instant.now();
        this.seasons = new ArrayList<>();
    }

    public void addSeason(LibrarySeason season) {
        season.setLibrarySeries(this);
        seasons.add(season);
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

    public Integer getFirstAirYear() {
        return firstAirYear;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public List<LibrarySeason> getSeasons() {
        return seasons;
    }
}
