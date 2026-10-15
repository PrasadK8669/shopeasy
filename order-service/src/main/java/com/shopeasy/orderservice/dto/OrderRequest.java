package com.shopeasy.orderservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record OrderRequest(
        @NotBlank(message = "Customer name is required") String customerName,
        @NotBlank(message = "Customer email is required")
        @Email(message = "Customer email must be valid") String customerEmail,
        @NotEmpty(message = "Order must contain at least one item")
        List<@Valid OrderItemRequest> items) {
}