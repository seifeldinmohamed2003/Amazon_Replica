package com.team27.amazon.order.repository;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    int countByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    List<Order> findByOrderedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Order> findByShippingAddressId(Long shippingAddressId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, OrderStatus status);

    long countByUserIdAndStatusIn(Long userId, List<OrderStatus> statuses);

    @Query("""
        SELECT COALESCE(SUM(o.totalAmount), 0)
        FROM Order o
        WHERE o.userId = :userId
          AND o.status IN :statuses
        """)
    double sumTotalAmountByUserIdAndStatusIn(
            @Param("userId") Long userId,
            @Param("statuses") List<OrderStatus> statuses
    );

    @Query(value = """
        SELECT *
        FROM orders
        WHERE metadata ->> :key = :value
        """, nativeQuery = true)
    List<Order> findByMetadataField(@Param("key") String key, @Param("value") String value);

    // Used by S3-F11 to load the order with its items
    @Query("""
        SELECT DISTINCT o
        FROM Order o
        LEFT JOIN FETCH o.orderItems oi
        WHERE o.id = :orderId
        """)
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

}
