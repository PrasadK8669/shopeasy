package com.shopeasy.productservice.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopeasy.productservice.dto.CategoryCount;
import com.shopeasy.productservice.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
              AND (:category = '' OR p.category = :category)
              AND p.price >= :minPrice
              AND p.price <= :maxPrice
              AND p.stock >= :minStock
            """)
    Page<Product> filter(@Param("search") String search,
                         @Param("category") String category,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("minStock") int minStock,
                         Pageable pageable);

    @Query("""
            SELECT new com.shopeasy.productservice.dto.CategoryCount(p.category, COUNT(p))
            FROM Product p
            WHERE p.category IS NOT NULL
            GROUP BY p.category
            ORDER BY p.category
            """)
    List<CategoryCount> countByCategory();

    // Atomic: checks and reduces in one statement. Returns 0 if there was not enough stock.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int reduceStockIfAvailable(@Param("id") Long id, @Param("qty") int qty);

    // Used to undo a reduction when an order fails later
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}