package com.streamvault.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Contract lives in docs/specs/design/story-005-api-contracts.md. RatingType does not exist yet;
 * this test is expected to fail to compile until Dev adds it.
 */
class RatingTypeTest {

    @Test
    void should_defineExactlyThreeOptions_when_valuesAreListed() {
        assertThat(RatingType.values()).hasSize(3);
    }

    @Test
    void should_includeAllThreeContractuallyRequiredOptions_when_valuesAreListed() {
        assertThat(RatingType.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "LOVE_LIKE_MEH_DISLIKE_HATE",
                        "THUMBS_UP_THUMBS_DOWN",
                        "HALF_STAR_OUT_OF_5");
    }
}
