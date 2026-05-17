package com.team27.amazon.order.repository;

import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    List<OrderItem> findByProductId(Long productId);

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0), COALESCE(SUM(oi.quantity * oi.priceAtPurchase), 0)
        FROM OrderItem oi
        WHERE oi.productId = :productId
          AND oi.order.status IN :statuses
          AND oi.order.deliveredAt BETWEEN :startDate AND :endDate
        """)
    Object[] productSalesAggregate(
            @Param("productId") Long productId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
        SELECT COUNT(oi)
        FROM OrderItem oi
        WHERE oi.productId = :productId
          AND oi.order.status = :status
        """)
    long countByProductIdAndOrderStatus(
            @Param("productId") Long productId,
            @Param("status") OrderStatus status
    );

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0)
        FROM OrderItem oi
        WHERE oi.productId = :productId
          AND oi.order.status IN :statuses
        """)
    long sumQuantityByProductIdAndOrderStatuses(
            @Param("productId") Long productId,
            @Param("statuses") List<OrderStatus> statuses
    );

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0)
        FROM OrderItem oi
        WHERE oi.productId = :productId
          AND oi.order.status IN :statuses
          AND oi.order.deliveredAt >= :since
        """)
    long sumQuantityByProductIdAndOrderStatusesSince(
            @Param("productId") Long productId,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("since") LocalDateTime since
    );

    @Query("""
        SELECT COUNT(oi) > 0
        FROM OrderItem oi
        WHERE oi.productId = :productId
          AND oi.order.userId = :userId
          AND oi.order.status IN :statuses
        """)
    boolean existsPurchasedProduct(
            @Param("userId") Long userId,
            @Param("productId") Long productId,
            @Param("statuses") List<OrderStatus> statuses
    );
}

