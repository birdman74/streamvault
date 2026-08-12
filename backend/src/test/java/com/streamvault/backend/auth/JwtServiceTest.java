package com.streamvault.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-that-is-at-least-32-bytes-long", 60_000L);
    }

    @Test
    void generatesTokenThatCanBeParsedBackToTheSameClaims() {
        String token = jwtService.generateToken(42L, "user@example.com");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
    }

    @Test
    void rejectsTokenSignedWithADifferentSecret() {
        JwtService otherService = new JwtService("a-completely-different-secret-key-32-bytes", 60_000L);
        String token = otherService.generateToken(1L, "user@example.com");

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        JwtService shortLivedService = new JwtService("test-secret-key-that-is-at-least-32-bytes-long", -1_000L);
        String token = shortLivedService.generateToken(1L, "user@example.com");

        assertThatThrownBy(() -> jwtService.parseClaims(token)).isInstanceOf(JwtException.class);
    }
}
