package com.team27.amazon.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team27.amazon.user.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // S1-F1
    @Query(value = """
        SELECT * FROM users
        WHERE (:name IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (:email IS NULL OR LOWER(email) LIKE LOWER(CONCAT('%', :email, '%')))
          AND (:role IS NULL OR role = :role)
        """, nativeQuery = true)
    List<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("role") String role
    );

    // S1-F3
    @Query(value = """
        SELECT
            u.id AS user_id,
            u.name AS name,
            COUNT(o.id) AS total_orders,
            SUM(CASE WHEN o.status = 'DELIVERED' THEN 1 ELSE 0 END) AS completed_orders,
            SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_orders,
            COALESCE(SUM(CASE WHEN o.status = 'DELIVERED' THEN o.total_amount ELSE 0 END), 0) AS total_spent,
            COALESCE(AVG(CASE WHEN o.status = 'DELIVERED' THEN o.total_amount END), 0) AS average_order_value
        FROM users u
        LEFT JOIN orders o ON u.id = o.user_id
        WHERE u.id = :userId
        GROUP BY u.id, u.name
        """, nativeQuery = true)
    Object[] getUserOrderSummary(@Param("userId") Long userId);

    //S1-F4
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM orders
                WHERE user_id = :userId
                  AND status IN ('PENDING', 'CONFIRMED', 'SHIPPED')
            )
            """, nativeQuery = true)
    boolean existsActiveOrdersByUserId(@Param("userId") Long userId);

    //S1-F5
    @Query(value = """
            SELECT *
            FROM users
            WHERE preferences ->> :key = :value
            """, nativeQuery = true)
    List<User> findUsersByPreference(@Param("key") String key, @Param("value") String value);

    //S1-F6
    @Query(value = """
        SELECT u.id AS user_id,
               u.name AS name,
               COALESCE(SUM(o.total_amount), 0) AS total_spent,
               COUNT(o.id) AS order_count
        FROM users u
        JOIN orders o ON u.id = o.user_id
        WHERE o.status = 'DELIVERED'
          AND o.ordered_at >= :startDateTime
          AND o.ordered_at < :endDateExclusive
        GROUP BY u.id, u.name
        ORDER BY total_spent DESC
        LIMIT :limitValue
        """, nativeQuery = true)
    List<Object[]> findTopBuyersByDateRange(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateExclusive") LocalDateTime endDateExclusive,
            @Param("limitValue") int limitValue
    );
    @Query(value = "SELECT u.* FROM public.users u " +
            "LEFT JOIN public.orders o ON o.user_id = u.id AND o.status = 'DELIVERED' " +
            "WHERE u.preferences ->> 'language' = :lang " +
            "GROUP BY u.id " +
            "HAVING COUNT(o.id) >= :minOrders",
            nativeQuery = true)
    List<User> findByLanguageAndMinOrders(@Param("lang") String lang, @Param("minOrders") long minOrders);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);
}