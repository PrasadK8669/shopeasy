package com.shopeasy.authservice.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verify your email address to sign in");
    }
}