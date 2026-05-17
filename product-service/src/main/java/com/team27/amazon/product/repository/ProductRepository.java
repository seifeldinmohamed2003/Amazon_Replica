package com.team27.amazon.product.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

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

    @Modifying
    @Query(value = """
            UPDATE products
            SET stock_quantity = stock_quantity - :quantity
            WHERE id = :productId
              AND stock_quantity >= :quantity
              AND status::text = 'ACTIVE'
            """, nativeQuery = true)
    int deductStockIfAvailable(@Param("productId") Long productId,
                               @Param("quantity") Integer quantity);

    @Modifying
    @Query(value = """
            UPDATE products
            SET stock_quantity = stock_quantity + :quantity
            WHERE id = :productId
            """, nativeQuery = true)
    int restoreStock(@Param("productId") Long productId,
                     @Param("quantity") Integer quantity);

    @Query(value = """
            SELECT *
            FROM products p
            ORDER BY p.rating DESC, p.total_ratings DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Product> findTopRatedProductEntities(@Param("limit") Integer limit);
}