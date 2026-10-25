package com.shopeasy.authservice.dto;

public record RegisterResponse(String email, boolean verificationRequired) {
}