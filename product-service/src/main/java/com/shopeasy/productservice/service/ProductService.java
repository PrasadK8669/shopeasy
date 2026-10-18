package com.shopeasy.productservice.service;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopeasy.productservice.entity.Product;
import com.shopeasy.productservice.exception.InsufficientStockException;
import com.shopeasy.productservice.exception.ProductNotFoundException;
import com.shopeasy.productservice.repository.ProductRepository;

@Service
public class ProductService {

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

    public Page<Product> getAll(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return productRepository.findAll(pageable);
        }
        return productRepository.searchByName(search.trim(), pageable);
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
    public List<Product> createAll(List<Product> products) {
    products.forEach(p -> p.setId(null));
    return productRepository.saveAll(products);
}

    @Transactional
    public Product reduceStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        Product product = getById(id);
        if (product.getStock() < quantity) {
            throw new InsufficientStockException(product.getName(), product.getStock(), quantity);
        }
        product.setStock(product.getStock() - quantity);
        return productRepository.save(product);
    }
    
}