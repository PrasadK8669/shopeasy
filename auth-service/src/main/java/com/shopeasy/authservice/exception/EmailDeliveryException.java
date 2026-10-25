package com.shopeasy.authservice.exception;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException() {
        super("We could not send the email right now. Try again in a minute.");
    }
}