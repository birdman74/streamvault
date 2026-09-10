package com.streamvault.backend.library;

/**
 * The epic-shared watch status for a library item. A movie has its status set directly at add time
 * (STORY-007) and changed later (STORY-010); a series derives it by roll-up (STORY-012). The wire
 * value is the plain constant name, matched case-sensitively, consistent with the {@code RatingType}
 * (STORY-005) and {@code TmdbMediaType} (STORY-006) precedent.
 */
public enum WatchStatus {
    PLANNED,
    CURRENTLY_WATCHING,
    WATCHED
}
