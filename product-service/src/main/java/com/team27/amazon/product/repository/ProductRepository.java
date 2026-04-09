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
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM order_items oi
        JOIN orders o ON oi.order_id = o.id
        WHERE oi.product_id = :productId
          AND o.status = 'PENDING'
        """, nativeQuery = true)
    boolean existsInPendingOrders(@Param("productId") Long productId);
}

