package com.streamvault.backend.auth;

public record AuthenticatedUser(Long userId, String email) {
}
