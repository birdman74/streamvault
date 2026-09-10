package com.streamvault.backend.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contract lives in docs/specs/design/story-007-api-contracts.md. {@code WatchStatus} does not exist
 * yet; this test is expected to fail to compile until Dev adds it.
 *
 * <p>Mirrors {@code RatingTypeTest}. {@code WatchStatus} is the epic-shared status enum (STORY-010
 * sets it on movies, STORY-012 derives it for series). This story only pins its three members and
 * their wire names (AC-3).
 */
class WatchStatusTest {

    @Test
    void should_defineExactlyThreeStatuses_when_valuesAreListed() {
        assertThat(WatchStatus.values()).hasSize(3);
    }

    @Test
    void should_containThePlannedCurrentlyWatchingAndWatchedConstants_when_valuesAreListed() {
        assertThat(WatchStatus.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("PLANNED", "CURRENTLY_WATCHING", "WATCHED");
    }
}
