package com.streamvault.backend.auth;

import com.streamvault.backend.auth.exception.GoogleSignInException;

/**
 * Seam between GoogleAuthService and the actual Google ID token verification mechanism.
 * Implementations throw GoogleSignInException on any verification failure.
 */
public interface GoogleTokenVerifier {

    GoogleUserInfo verify(String idToken);
}
