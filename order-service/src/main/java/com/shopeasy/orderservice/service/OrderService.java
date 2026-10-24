package com.shopeasy.orderservice.service;

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
import com.shopeasy.orderservice.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private record Reduction(Long productId, int quantity) {
    }

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public OrderResponse placeOrder(OrderRequest request, CurrentUser user) {

        // 1. Merge duplicate products (the same product listed twice becomes one line)
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        // 2. Friendly pre-check. This only fails fast with a clear message; it does not protect the stock.
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

        // 3. Reduce the stock (atomic in Product Service). Undo it if anything fails afterwards.
        List<Reduction> reduced = new ArrayList<>();
        try {
            for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
                productClient.reduceStock(entry.getKey(), entry.getValue());
                reduced.add(new Reduction(entry.getKey(), entry.getValue()));
            }
            Order order = buildOrder(user, quantities, products);
            return OrderResponse.from(orderRepository.save(order));
        } catch (RuntimeException e) {
            restoreStock(reduced);
            throw e;
        }
    }

    private Order buildOrder(CurrentUser user, Map<Long, Integer> quantities,
                             Map<Long, ProductResponse> products) {
        Order order = new Order();
        // Identity comes from the signed token, never from the request body
        order.setUserId(user.id());
        order.setCustomerName(user.name());
        order.setCustomerEmail(user.email());
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
        return order;
    }

    private void restoreStock(List<Reduction> reduced) {
        for (Reduction r : reduced) {
            try {
                productClient.restoreStock(r.productId(), r.quantity());
            } catch (RuntimeException ex) {
                log.error("Could not restore {} unit(s) of product {} after a failed order. Fix the stock manually.",
                        r.quantity(), r.productId(), ex);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(CurrentUser user) {
        List<Order> orders = user.admin()
                ? orderRepository.findAllByOrderByIdDesc()
                : orderRepository.findAllByUserIdOrderByIdDesc(user.id());
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, CurrentUser user) {
        Order order = orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (!user.admin() && !Objects.equals(order.getUserId(), user.id())) {
            // Same answer as "does not exist", so other people's order numbers are not revealed
            throw new OrderNotFoundException(id);
        }
        return OrderResponse.from(order);
    }
}