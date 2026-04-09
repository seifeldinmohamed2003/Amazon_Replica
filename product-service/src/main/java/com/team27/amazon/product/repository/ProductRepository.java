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
        SELECT 
            p.id AS productId,
            p.name AS name,
            p.rating AS rating,
            COALESCE(COUNT(CASE WHEN o.id IS NOT NULL THEN oi.id END), 0) AS totalSales
        FROM products p
        LEFT JOIN order_items oi ON p.id = oi.product_id
        LEFT JOIN orders o ON oi.order_id = o.id AND o.status = 'DELIVERED'
        GROUP BY p.id, p.name, p.rating
        ORDER BY p.rating DESC
        LIMIT :limit
        """, nativeQuery = true)
List<Object[]> findTopRatedProducts(@Param("limit") int limit);
}

