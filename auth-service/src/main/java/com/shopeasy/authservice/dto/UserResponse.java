package com.shopeasy.authservice.dto;

public record UserResponse(Long id, String name, String email, String role) {
}