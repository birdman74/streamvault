package com.streamvault.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void googleUserFactoryCreatesUserWithNullPasswordHashAndGivenGoogleIdAndEmail() {
        User user = User.googleUser("user@example.com", "google-subject-12345");

        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleId()).isEqualTo("google-subject-12345");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
    }

    /**
     * Gap identified in Brian's PR #4 review (User.java:23): password_hash became nullable in
     * STORY-002 so Google-linked users can omit it, but nothing stops a local (non-Google) account
     * from being constructed the same way. The public two-arg constructor is the only path
     * AuthService.register() uses to create local accounts, so it must reject a missing password
     * hash itself rather than relying on callers to remember to check.
     */
    @Test
    void should_throwIllegalArgumentException_when_localAccountConstructedWithNullPasswordHash() {
        assertThatThrownBy(() -> new User("user@example.com", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_throwIllegalArgumentException_when_localAccountConstructedWithBlankPasswordHash() {
        assertThatThrownBy(() -> new User("user@example.com", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * story-005 AC-3: a newly registered account defaults to "Love / Like / Meh / Dislike / Hate"
     * without the user taking any action. Covers both existing registration paths (STORY-001 local
     * account, STORY-002 Google account) so the default holds regardless of which factory a caller
     * uses.
     */
    @Test
    void should_defaultToLoveLikeMehDislikeHateRatingType_when_localAccountIsConstructed() {
        User user = new User("user@example.com", "hashed-password");

        assertThat(user.getRatingType()).isEqualTo(RatingType.LOVE_LIKE_MEH_DISLIKE_HATE);
    }

    @Test
    void should_defaultToLoveLikeMehDislikeHateRatingType_when_googleUserIsConstructed() {
        User user = User.googleUser("user@example.com", "google-subject-12345");

        assertThat(user.getRatingType()).isEqualTo(RatingType.LOVE_LIKE_MEH_DISLIKE_HATE);
    }
}
