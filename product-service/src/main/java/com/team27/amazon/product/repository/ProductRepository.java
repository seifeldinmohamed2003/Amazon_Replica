package com.team27.amazon.product.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategoryIgnoreCase(String category);

    @Query(value = """
            SELECT *
            FROM products p
            WHERE p.specifications ->> :key = :value
              AND (:status IS NULL OR p.status = CAST(:status AS VARCHAR))
            """, nativeQuery = true)
    List<Product> findBySpecificationKeyValueAndOptionalStatus(
            @Param("key") String key,
            @Param("value") String value,
            @Param("status") String status
    );
}