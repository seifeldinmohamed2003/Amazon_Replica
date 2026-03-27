package com.team27.amazon.product.repository;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategoryIgnoreCase(String category);

    @Query(value = "SELECT COALESCE(SUM(oi.quantity), 0) AS total_units_sold, " +
            "COALESCE(SUM(oi.quantity * oi.price_at_purchase), 0) AS total_revenue " +
            "FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.id " +
            "WHERE oi.product_id = :productId " +
            "AND o.status = 'DELIVERED' " +
            "AND o.delivered_at BETWEEN :startDateTime AND :endDateTime",
            nativeQuery = true)
    Object[] getProductSalesSummary(
            @Param("productId") Long productId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );
}

