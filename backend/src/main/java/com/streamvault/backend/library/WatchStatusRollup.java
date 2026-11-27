package com.streamvault.backend.library;

import java.util.List;

/**
 * Pure, stateless derivation of a rolled-up {@link WatchStatus} from a list of child statuses,
 * per STORY-012 AC-2/AC-3's rule: Watched when every child is Watched, Planned when every child
 * is Planned, Currently Watching otherwise, with an empty grouping treated as Planned. Reused
 * unchanged by STORY-012 rather than re-derived there (story-009-api-contracts.md, "Spec
 * clarification already surfaced to Brian").
 */
public final class WatchStatusRollup {

    private WatchStatusRollup() {
    }

    public static WatchStatus of(List<WatchStatus> statuses) {
        if (statuses.isEmpty()) {
            return WatchStatus.PLANNED;
        }
        boolean allWatched = statuses.stream().allMatch(status -> status == WatchStatus.WATCHED);
        if (allWatched) {
            return WatchStatus.WATCHED;
        }
        boolean allPlanned = statuses.stream().allMatch(status -> status == WatchStatus.PLANNED);
        if (allPlanned) {
            return WatchStatus.PLANNED;
        }
        return WatchStatus.CURRENTLY_WATCHING;
    }

    public static WatchStatus seriesStatus(LibrarySeries series) {
        List<WatchStatus> seasonStatuses = series.getSeasons().stream()
                .map(season -> of(season.getEpisodes().stream()
                        .map(LibraryEpisode::getStatus)
                        .toList()))
                .toList();
        return of(seasonStatuses);
    }
}
