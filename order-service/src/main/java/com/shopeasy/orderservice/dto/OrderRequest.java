package com.shopeasy.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderRequest(
        @NotEmpty(message = "Order must contain at least one item")
        @Size(max = 50, message = "An order can contain at most 50 different products")
        List<@Valid OrderItemRequest> items) {
}