package com.shopeasy.authservice.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {
}