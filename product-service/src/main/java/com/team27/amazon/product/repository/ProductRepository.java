package com.team27.amazon.product.repository;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByCategoryIgnoreCase(String category);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM users u
            WHERE u.id = :userId
            """, nativeQuery = true)
    boolean userExists(@Param("userId") Long userId);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            WHERE o.user_id = :userId
              AND oi.product_id = :productId
              AND o.status = 'DELIVERED'
            """, nativeQuery = true)
    boolean hasDeliveredPurchase(@Param("userId") Long userId,
                                 @Param("productId") Long productId);
}