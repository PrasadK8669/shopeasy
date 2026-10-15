package com.shopeasy.productservice.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productName, int available, int requested) {
        super("Insufficient stock for " + productName
                + ". Available: " + available + ", requested: " + requested);
    }
}