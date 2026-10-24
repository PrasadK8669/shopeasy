package com.shopeasy.productservice.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shopeasy.productservice.dto.CategoryCount;
import com.shopeasy.productservice.dto.StockRequest;
import com.shopeasy.productservice.entity.Product;
import com.shopeasy.productservice.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<Product> getAll(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String category,
                                @RequestParam(required = false) BigDecimal minPrice,
                                @RequestParam(required = false) BigDecimal maxPrice,
                                @RequestParam(defaultValue = "false") boolean inStock,
                                @RequestParam(defaultValue = "featured") String sort,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "12") int size) {
        return productService.getAll(search, category, minPrice, maxPrice, inStock, sort, page, size);
    }

    @GetMapping("/categories")
    public List<CategoryCount> getCategories() {
        return productService.getCategories();
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    public ResponseEntity<Product> create(@Valid @RequestBody Product product) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(product));
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody Product product) {
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    public Product reduceStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return productService.reduceStock(id, request.quantity());
    }
        @PatchMapping("/{id}/stock/restore")
    public Product restoreStock(@PathVariable Long id, @Valid @RequestBody StockRequest request) {
        return productService.restoreStock(id, request.quantity());
    }
}