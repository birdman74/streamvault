package com.streamvault.backend.library;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One season within a {@link LibrarySeries}, including season {@code 0} for specials, stored as
 * its own grouping (AC-9). {@link #addEpisode(LibraryEpisode)} wires both sides of the
 * relationship; the {@code library_series_id} side is wired by
 * {@link LibrarySeries#addSeason(LibrarySeason)}, not here.
 */
@Entity
@Table(name = "library_seasons")
public class LibrarySeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_series_id", nullable = false)
    private LibrarySeries librarySeries;

    @Column(name = "season_number", nullable = false)
    private int seasonNumber;

    @OneToMany(mappedBy = "librarySeason", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LibraryEpisode> episodes = new ArrayList<>();

    protected LibrarySeason() {
    }

    public LibrarySeason(int seasonNumber) {
        this.seasonNumber = seasonNumber;
        this.episodes = new ArrayList<>();
    }

    public void addEpisode(LibraryEpisode episode) {
        episode.setLibrarySeason(this);
        episodes.add(episode);
    }

    public Long getId() {
        return id;
    }

    public LibrarySeries getLibrarySeries() {
        return librarySeries;
    }

    void setLibrarySeries(LibrarySeries librarySeries) {
        this.librarySeries = librarySeries;
    }

    public int getSeasonNumber() {
        return seasonNumber;
    }

    public List<LibraryEpisode> getEpisodes() {
        return episodes;
    }
}
