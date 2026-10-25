package com.shopeasy.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginResendRequest(
        @NotBlank(message = "Sign-in session is required")
        @Size(max = 2000, message = "Sign-in session is not valid") String challengeToken) {
}