package com.streamvault.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void googleUserFactoryCreatesUserWithNullPasswordHashAndGivenGoogleIdAndEmail() {
        User user = User.googleUser("user@example.com", "google-subject-12345");

        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getGoogleId()).isEqualTo("google-subject-12345");
        assertThat(user.getEmail()).isEqualTo("user@example.com");
    }
}
