package com.shopeasy.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginVerifyRequest(
        @NotBlank(message = "Sign-in session is required")
        @Size(max = 2000, message = "Sign-in session is not valid") String challengeToken,

        @NotBlank(message = "Code is required")
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String code) {
}