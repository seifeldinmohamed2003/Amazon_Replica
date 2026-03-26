package com.team27.amazon.product.repository;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategoryIgnoreCase(String category);
}

