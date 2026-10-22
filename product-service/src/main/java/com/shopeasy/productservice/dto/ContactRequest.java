package com.shopeasy.productservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be 100 characters or fewer") String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be 150 characters or fewer") String email,

        @NotBlank(message = "Topic is required")
        @Pattern(regexp = "general|order|feedback|other", message = "Choose a topic from the list") String topic,

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters") String message) {
}