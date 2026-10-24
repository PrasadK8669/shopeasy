package com.shopeasy.orderservice.controller;

import com.shopeasy.orderservice.dto.OrderRequest;
import com.shopeasy.orderservice.dto.OrderResponse;
import com.shopeasy.orderservice.security.CurrentUser;
import com.shopeasy.orderservice.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request,
                                                    JwtAuthenticationToken auth) {
        OrderResponse response = orderService.placeOrder(request, CurrentUser.from(auth));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<OrderResponse> getOrders(JwtAuthenticationToken auth) {
        return orderService.getOrders(CurrentUser.from(auth));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id, JwtAuthenticationToken auth) {
        return orderService.getOrderById(id, CurrentUser.from(auth));
    }
}