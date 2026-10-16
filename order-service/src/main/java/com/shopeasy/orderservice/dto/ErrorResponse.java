package com.shopeasy.orderservice.dto;

public record ErrorResponse(int status, String error, String message) {
}