package com.streamvault.backend.auth;

public record GoogleUserInfo(String googleId, String email, boolean emailVerified) {
}
