package com.shopeasy.productservice.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.productservice.dto.CategoryCount;
import com.shopeasy.productservice.entity.Product;
import com.shopeasy.productservice.exception.InsufficientStockException;
import com.shopeasy.productservice.exception.ProductNotFoundException;
import com.shopeasy.productservice.repository.ProductRepository;

@Service
public class ProductService {

    private static final BigDecimal NO_MAX_PRICE = new BigDecimal("99999999.99");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(Product product) {
        product.setId(null); // make sure a new row is always created
        return productRepository.save(product);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public Page<Product> getAll(String search, String category, BigDecimal minPrice,
                                BigDecimal maxPrice, boolean inStock, String sort,
                                int page, int size) {
        String keyword = search == null ? "" : search.trim();
        String categoryFilter = category == null ? "" : category.trim();
        BigDecimal min = minPrice == null ? BigDecimal.ZERO : minPrice;
        BigDecimal max = maxPrice == null ? NO_MAX_PRICE : maxPrice;
        int minStock = inStock ? 1 : 0;

        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        return productRepository.filter(keyword, categoryFilter, min, max, minStock, pageable);
    }

    public List<CategoryCount> getCategories() {
        return productRepository.countByCategory();
    }

    public Product update(Long id, Product updated) {
        Product existing = getById(id);
        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setPrice(updated.getPrice());
        existing.setStock(updated.getStock());
        existing.setCategory(updated.getCategory());
        existing.setImageUrl(updated.getImageUrl());
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        Product existing = getById(id);
        productRepository.delete(existing);
    }

        @Transactional
    public Product reduceStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        // One atomic statement: the database checks and reduces in the same step
        int updated = productRepository.reduceStockIfAvailable(id, quantity);
        if (updated == 0) {
            Product product = getById(id); // throws 404 if the product does not exist
            throw new InsufficientStockException(product.getName(), product.getStock(), quantity);
        }
        return getById(id);
    }

    @Transactional
    public Product restoreStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        if (productRepository.increaseStock(id, quantity) == 0) {
            throw new ProductNotFoundException(id);
        }
        return getById(id);
    }

    // Only these four sort options are allowed; anything else falls back to the default
    private Sort toSort(String sort) {
        if (sort == null) {
            return Sort.by("id");
        }
        return switch (sort) {
            case "price-asc" -> Sort.by("price").ascending().and(Sort.by("id"));
            case "price-desc" -> Sort.by("price").descending().and(Sort.by("id"));
            case "name" -> Sort.by("name").ascending().and(Sort.by("id"));
            default -> Sort.by("id");
        };
    }
}