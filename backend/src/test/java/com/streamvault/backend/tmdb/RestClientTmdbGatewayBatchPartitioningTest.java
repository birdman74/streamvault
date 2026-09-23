package com.streamvault.backend.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Dev-authored lower-level unit test below Test's integration boundary (see
 * docs/specs/design/story-008-agreed.md "Test Coverage Confirmation"). Pins
 * {@link RestClientTmdbGateway#partitionIntoBatches(List, int)} as a pure function, no
 * {@code MockRestServiceServer} involved, complementing
 * {@code RestClientTmdbGatewaySeriesTest}'s HTTP-level batching assertions with every boundary
 * count around the batch size of 20: 0, 1, 20, 21, 40, 41 seasons.
 */
class RestClientTmdbGatewayBatchPartitioningTest {

    private static final int BATCH_SIZE = 20;

    private static List<Integer> seasonNumbers(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    @Test
    void should_returnNoBatches_when_thereAreZeroSeasons() {
        List<List<Integer>> batches = RestClientTmdbGateway.partitionIntoBatches(List.of(), BATCH_SIZE);

        assertThat(batches).isEmpty();
    }

    @Test
    void should_returnOneSingleElementBatch_when_thereIsOneSeason() {
        List<List<Integer>> batches =
                RestClientTmdbGateway.partitionIntoBatches(seasonNumbers(1), BATCH_SIZE);

        assertThat(batches).containsExactly(List.of(1));
    }

    @Test
    void should_returnExactlyOneFullBatch_when_thereAreExactly20Seasons() {
        List<List<Integer>> batches =
                RestClientTmdbGateway.partitionIntoBatches(seasonNumbers(20), BATCH_SIZE);

        assertThat(batches).hasSize(1);
        assertThat(batches.get(0)).hasSize(20).containsExactlyElementsOf(seasonNumbers(20));
    }

    @Test
    void should_returnAFullBatchAndAOneElementBatch_when_thereAre21Seasons() {
        List<List<Integer>> batches =
                RestClientTmdbGateway.partitionIntoBatches(seasonNumbers(21), BATCH_SIZE);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(20).containsExactlyElementsOf(seasonNumbers(20));
        assertThat(batches.get(1)).containsExactly(21);
    }

    @Test
    void should_returnExactlyTwoFullBatches_when_thereAreExactly40Seasons() {
        List<List<Integer>> batches =
                RestClientTmdbGateway.partitionIntoBatches(seasonNumbers(40), BATCH_SIZE);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0)).hasSize(20);
        assertThat(batches.get(1)).hasSize(20);
        assertThat(batches.get(1).get(19)).isEqualTo(40);
    }

    @Test
    void should_returnTwoFullBatchesAndAOneElementBatch_when_thereAre41Seasons() {
        List<List<Integer>> batches =
                RestClientTmdbGateway.partitionIntoBatches(seasonNumbers(41), BATCH_SIZE);

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(20);
        assertThat(batches.get(1)).hasSize(20);
        assertThat(batches.get(2)).containsExactly(41);
    }
}
