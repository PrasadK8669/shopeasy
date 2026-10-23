package com.shopeasy.authservice.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("An account with this email already exists");
    }
}