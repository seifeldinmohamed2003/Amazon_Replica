package com.team27.amazon.product.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);

        @Query("SELECT p FROM Product p WHERE LOWER(CAST(p.category AS string)) = LOWER(:category)")
        List<Product> findByCategoryIgnoreCase(@Param("category") String category);

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


    @Query(value = """
        SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END
        FROM order_items oi
        JOIN orders o ON oi.order_id = o.id
        WHERE oi.product_id = :productId
          AND o.status = 'PENDING'
        """, nativeQuery = true)
    boolean existsInPendingOrders(@Param("productId") Long productId);

    List<Product> findByStockQuantityLessThanOrderByStockQuantityAsc(Integer threshold);

    @Query("SELECT p FROM Product p WHERE " +
            "(:category IS NULL OR LOWER(CAST(p.category AS string)) = LOWER(:category)) " +
           "AND p.price >= :minPrice AND p.price <= :maxPrice " +
           "ORDER BY p.price ASC")
    List<Product> searchByPriceRange(
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("category") String category
    );

    @Query(value = "SELECT COALESCE(SUM(oi.quantity), 0) AS total_units_sold, " +
            "COALESCE(SUM(oi.quantity * oi.price_at_purchase), 0) AS total_revenue " +
            "FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.id " +
            "WHERE oi.product_id = :productId " +
            "AND o.status = 'DELIVERED' " +
            "AND o.ordered_at BETWEEN :startDateTime AND :endDateTime",
            nativeQuery = true)
    Object[] getProductSalesSummary(
            @Param("productId") Long productId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime
    );

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

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM users u
            WHERE u.id = :userId
              AND u.role = 'ADMIN'
            """, nativeQuery = true)
    boolean isAdminUser(@Param("userId") Long userId);
          
    @Query(value = """
         SELECT *
            FROM products p
            WHERE p.specifications ->> :key = :value
                                                        AND (:status IS NULL OR p.status::text = :status)
            """, nativeQuery = true)
    List<Product> findBySpecificationKeyValueAndOptionalStatus(
            @Param("key") String key,
            @Param("value") String value,
            @Param("status") String status
    );
}