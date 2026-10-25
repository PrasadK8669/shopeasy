package com.shopeasy.authservice.exception;

public class InvalidChallengeException extends RuntimeException {

    public InvalidChallengeException() {
        super("Your sign-in session expired. Sign in again.");
    }
}