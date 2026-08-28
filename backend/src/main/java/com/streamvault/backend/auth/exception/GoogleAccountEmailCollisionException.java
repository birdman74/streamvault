package com.streamvault.backend.auth.exception;

public class GoogleAccountEmailCollisionException extends RuntimeException {

    public GoogleAccountEmailCollisionException() {
        super("An account with this email already exists");
    }
}
