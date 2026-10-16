package com.shopeasy.orderservice.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.orderservice.client.ProductClient;
import com.shopeasy.orderservice.dto.OrderItemRequest;
import com.shopeasy.orderservice.dto.OrderRequest;
import com.shopeasy.orderservice.dto.OrderResponse;
import com.shopeasy.orderservice.dto.ProductResponse;
import com.shopeasy.orderservice.entity.Order;
import com.shopeasy.orderservice.entity.OrderItem;
import com.shopeasy.orderservice.entity.OrderStatus;
import com.shopeasy.orderservice.exception.InsufficientStockException;
import com.shopeasy.orderservice.exception.OrderNotFoundException;
import com.shopeasy.orderservice.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public OrderResponse placeOrder(OrderRequest request) {

        // 1. Merge duplicate products (the same product listed twice becomes one line)
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // 2. Fetch every product and check the stock BEFORE changing anything
        Map<Long, ProductResponse> products = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            ProductResponse product = productClient.getProduct(entry.getKey());
            if (product.stock() < entry.getValue()) {
                throw new InsufficientStockException(
                        "Insufficient stock for " + product.name()
                                + ". Available: " + product.stock()
                                + ", requested: " + entry.getValue());
            }
            products.put(entry.getKey(), product);
        }

        // 3. Reduce the stock for every product
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            productClient.reduceStock(entry.getKey(), entry.getValue());
        }

        // 4. Build the order, copying the name and price into each item (a snapshot)
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setStatus(OrderStatus.PLACED);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            ProductResponse product = products.get(entry.getKey());

            OrderItem item = new OrderItem();
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
            item.setQuantity(entry.getValue());
            order.addItem(item);

            total = total.add(product.price().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        order.setTotalAmount(total);

        // 5. Save the order and its items
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByIdDesc().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }
}