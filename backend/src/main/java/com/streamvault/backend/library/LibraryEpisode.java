package com.streamvault.backend.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One episode within a {@link LibrarySeason}. Getters only, no setters: every field is set once at
 * construction (AC-4, every episode starts {@code PLANNED}). The {@code library_season_id} side is
 * wired by {@link LibrarySeason#addEpisode(LibraryEpisode)}, not here.
 */
@Entity
@Table(name = "library_episodes")
public class LibraryEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_season_id", nullable = false)
    private LibrarySeason librarySeason;

    @Column(name = "episode_number", nullable = false)
    private int episodeNumber;

    @Column(name = "title", length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WatchStatus status;

    protected LibraryEpisode() {
    }

    public LibraryEpisode(int episodeNumber, String title, WatchStatus status) {
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public LibrarySeason getLibrarySeason() {
        return librarySeason;
    }

    void setLibrarySeason(LibrarySeason librarySeason) {
        this.librarySeason = librarySeason;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public WatchStatus getStatus() {
        return status;
    }
}
