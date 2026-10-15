package com.shopeasy.productservice.dto;

import jakarta.validation.constraints.Min;

public record StockRequest(
        @Min(value = 1, message = "Quantity must be at least 1") int quantity) {
}