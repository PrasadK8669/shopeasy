package com.shopeasy.productservice.dto;

public record ErrorResponse(int status, String error, String message) {
}