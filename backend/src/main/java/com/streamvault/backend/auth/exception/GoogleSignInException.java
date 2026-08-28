package com.streamvault.backend.auth.exception;

public class GoogleSignInException extends RuntimeException {

    public GoogleSignInException() {
        super("Google sign-in failed");
    }
}
