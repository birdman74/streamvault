package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Contract lives in docs/specs/design/story-009-api-contracts.md. {@code WatchStatusRollup} does
 * not exist yet; this test is expected to fail to compile until Dev implements it.
 *
 * <p>Plain JUnit, no Spring context, no mocks. Pins the exact derivation rule specified by
 * STORY-012 AC-2/AC-3 (Watched when every child is Watched, Planned when every child is Planned,
 * Currently Watching otherwise; an empty grouping is Planned) so STORY-012's own design phase has a
 * passing reference implementation to reuse rather than re-deriving the rule. The two-level
 * {@code seriesStatus} test builds a real {@code LibrarySeries} fixture via the existing
 * {@code addSeason} / {@code addEpisode} helpers, usable outside a persistence context since these
 * are plain JPA entities.
 */
class WatchStatusRollupTest {

    @Test
    void should_rollUpToPlanned_when_theGroupingIsEmpty() {
        assertThat(WatchStatusRollup.of(List.of())).isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_rollUpToWatched_when_everyStatusIsWatched() {
        assertThat(WatchStatusRollup.of(
                List.of(WatchStatus.WATCHED, WatchStatus.WATCHED, WatchStatus.WATCHED)))
                .isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void should_rollUpToPlanned_when_everyStatusIsPlanned() {
        assertThat(WatchStatusRollup.of(
                List.of(WatchStatus.PLANNED, WatchStatus.PLANNED)))
                .isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_rollUpToCurrentlyWatching_when_statusesAreAMixOfAllThreeValues() {
        assertThat(WatchStatusRollup.of(
                List.of(WatchStatus.WATCHED, WatchStatus.PLANNED, WatchStatus.CURRENTLY_WATCHING)))
                .isEqualTo(WatchStatus.CURRENTLY_WATCHING);
    }

    @Test
    void should_rollUpToCurrentlyWatching_when_watchedAndPlannedAreMixedWithNoExplicitCurrentlyWatching() {
        assertThat(WatchStatusRollup.of(
                List.of(WatchStatus.WATCHED, WatchStatus.PLANNED)))
                .isEqualTo(WatchStatus.CURRENTLY_WATCHING);
    }

    @Test
    void should_rollUpToCurrentlyWatching_when_anyStatusIsCurrentlyWatchingEvenIfOthersAreWatched() {
        assertThat(WatchStatusRollup.of(
                List.of(WatchStatus.WATCHED, WatchStatus.WATCHED, WatchStatus.CURRENTLY_WATCHING)))
                .isEqualTo(WatchStatus.CURRENTLY_WATCHING);
    }

    @Test
    void should_rollUpToWatched_when_onlyOneStatusIsGivenAndItIsWatched() {
        assertThat(WatchStatusRollup.of(List.of(WatchStatus.WATCHED))).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void should_rollUpThroughTwoLevels_when_computingASeriesStatusFromItsSeasonsAndEpisodes() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Game of Thrones", 2011, null);

        LibrarySeason fullyWatchedSeason = new LibrarySeason(1);
        fullyWatchedSeason.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.WATCHED));
        fullyWatchedSeason.addEpisode(new LibraryEpisode(2, "The Kingsroad", WatchStatus.WATCHED));
        series.addSeason(fullyWatchedSeason);

        LibrarySeason fullyPlannedSeason = new LibrarySeason(2);
        fullyPlannedSeason.addEpisode(new LibraryEpisode(1, "The North Remembers", WatchStatus.PLANNED));
        series.addSeason(fullyPlannedSeason);

        assertThat(WatchStatusRollup.seriesStatus(series)).isEqualTo(WatchStatus.CURRENTLY_WATCHING);
    }

    @Test
    void should_rollUpToWatched_when_everySeasonIsFullyWatched() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Game of Thrones", 2011, null);

        LibrarySeason seasonOne = new LibrarySeason(1);
        seasonOne.addEpisode(new LibraryEpisode(1, "Winter Is Coming", WatchStatus.WATCHED));
        series.addSeason(seasonOne);

        LibrarySeason seasonTwo = new LibrarySeason(2);
        seasonTwo.addEpisode(new LibraryEpisode(1, "The North Remembers", WatchStatus.WATCHED));
        series.addSeason(seasonTwo);

        assertThat(WatchStatusRollup.seriesStatus(series)).isEqualTo(WatchStatus.WATCHED);
    }

    @Test
    void should_rollUpToPlanned_when_theSeriesHasNoSeasonsAtAll() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Empty Series", null, null);

        assertThat(WatchStatusRollup.seriesStatus(series)).isEqualTo(WatchStatus.PLANNED);
    }

    @Test
    void should_treatASeasonWithNoEpisodesAsPlanned_whenRollingUpTheSeries() {
        LibrarySeries series = new LibrarySeries(1L, 1399L, "Series With An Empty Season", 2011, null);

        LibrarySeason seasonWithEpisodes = new LibrarySeason(1);
        seasonWithEpisodes.addEpisode(new LibraryEpisode(1, "Pilot", WatchStatus.WATCHED));
        series.addSeason(seasonWithEpisodes);

        LibrarySeason emptySeason = new LibrarySeason(2);
        series.addSeason(emptySeason);

        assertThat(WatchStatusRollup.seriesStatus(series)).isEqualTo(WatchStatus.CURRENTLY_WATCHING);
    }
}
