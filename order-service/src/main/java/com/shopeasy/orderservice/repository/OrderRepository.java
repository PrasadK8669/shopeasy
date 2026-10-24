package com.shopeasy.orderservice.repository;

import com.shopeasy.orderservice.entity.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByIdDesc();

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByUserIdOrderByIdDesc(Long userId);

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}