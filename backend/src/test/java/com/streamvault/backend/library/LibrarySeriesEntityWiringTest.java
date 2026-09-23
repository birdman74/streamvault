package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-008-agreed.md "Test Coverage Confirmation"). Pins the
 * both-sides-set invariant of {@code LibrarySeries#addSeason} and {@code LibrarySeason#addEpisode}
 * directly, in isolation from {@code LibrarySeriesRepositoryTest}'s round-trip (which exercises the
 * same wiring incidentally, through persistence).
 */
class LibrarySeriesEntityWiringTest {

    @Test
    void should_wireBothSides_when_aSeasonIsAddedToASeries() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason season = new LibrarySeason(1);

        series.addSeason(season);

        assertThat(series.getSeasons()).containsExactly(season);
        assertThat(season.getLibrarySeries()).isSameAs(series);
    }

    @Test
    void should_wireBothSides_when_anEpisodeIsAddedToASeason() {
        LibrarySeason season = new LibrarySeason(1);
        LibraryEpisode episode = new LibraryEpisode(1, "Winter Is Coming", WatchStatus.PLANNED);

        season.addEpisode(episode);

        assertThat(season.getEpisodes()).containsExactly(episode);
        assertThat(episode.getLibrarySeason()).isSameAs(season);
    }

    @Test
    void should_leaveEpisodesAndSeasonsEmpty_when_constructedWithoutAnyAdded() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Game of Thrones", 2011, null);
        LibrarySeason season = new LibrarySeason(1);

        assertThat(series.getSeasons()).isEmpty();
        assertThat(season.getEpisodes()).isEmpty();
    }
}
