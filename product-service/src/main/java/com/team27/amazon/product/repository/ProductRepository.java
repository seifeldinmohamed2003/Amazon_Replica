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

    @Query(value = """
            SELECT *
            FROM products p
            WHERE LOWER(p.category::text) = LOWER(:category)
            """, nativeQuery = true)
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

    @Query(value = """
    SELECT *
    FROM products p
    WHERE p.stock_quantity < :threshold
    ORDER BY p.stock_quantity ASC
    """, nativeQuery = true)
    List<Product> findLowStockProducts(@Param("threshold") Integer threshold);

    @Query(value = """
            SELECT *
            FROM products p
            WHERE (:category IS NULL OR LOWER(p.category::text) = LOWER(:category))
              AND p.price >= :minPrice
              AND p.price <= :maxPrice
            ORDER BY p.price ASC
            """, nativeQuery = true)
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
            "AND o.status::text = 'DELIVERED' " +
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

        @Query(value = "SELECT COUNT(*) FROM products", nativeQuery = true)
    Long countAllProductsForDashboard();

      @Query(value = """
            SELECT COUNT(*)
            FROM products p
            WHERE p.status::text = 'OUT_OF_STOCK'
            """, nativeQuery = true)
    Long countOutOfStockProductsForDashboard();

    @Query(value = """
            SELECT COALESCE(AVG(p.rating), 0)
            FROM products p
            WHERE p.total_ratings > 0
            """, nativeQuery = true)
    Double averageRatedProductsForDashboard();

    @Query(value = """
            SELECT p.category::text AS category, COUNT(*) AS count
            FROM products p
            GROUP BY p.category::text
            """, nativeQuery = true)
    List<Object[]> countProductsByCategoryForDashboard();

    @Query(value = """
            SELECT COALESCE(AVG(p.price), 0)
            FROM products p
            """, nativeQuery = true)
    Double averagePriceForDashboard();

    @Query(value = """
            SELECT COUNT(*)
            FROM products p
            WHERE p.stock_quantity <= 10
              AND p.status::text = 'ACTIVE'
            """, nativeQuery = true)
    Long countLowStockActiveProductsForDashboard();
}