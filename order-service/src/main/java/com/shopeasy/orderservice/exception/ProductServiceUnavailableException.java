package com.shopeasy.orderservice.exception;

public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException() {
        super("Product service is currently unavailable. Please try again later.");
    }
}