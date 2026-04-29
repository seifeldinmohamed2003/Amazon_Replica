package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(value = """
        SELECT * FROM transactions
        WHERE (:status IS NULL OR status::text = :status)
        AND created_at BETWEEN :startDate AND :endDate
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Transaction> searchTransactions(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    int countUserById(@Param("userId") Long userId);

    @Query(value = """
        SELECT method::text, COUNT(*) as cnt, SUM(amount) as total
        FROM transactions
        WHERE user_id = :userId AND status::text = 'COMPLETED'
        GROUP BY method
        """, nativeQuery = true)
    List<Object[]> getTransactionSummaryByUser(@Param("userId") Long userId);

    @Query(value = "SELECT status FROM orders WHERE id = :orderId", nativeQuery = true)
    String findOrderStatusById(@Param("orderId") Long orderId);

    @Query(value = "SELECT total_amount FROM orders WHERE id = :orderId", nativeQuery = true)
    Double findOrderTotalAmountById(@Param("orderId") Long orderId);

    @Query(value = "SELECT * FROM transactions WHERE order_id = :orderId AND status::text = 'PENDING' LIMIT 1", nativeQuery = true)
    Optional<Transaction> findPendingTransactionByOrderId(@Param("orderId") Long orderId);

    @Query(value = "SELECT COUNT(*) FROM transactions WHERE order_id = :orderId AND status::text = 'COMPLETED'", nativeQuery = true)
    int countCompletedTransactionsByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t " +
            "WHERE t.status = com.team27.amazon.billing.model.TransactionStatus.COMPLETED " +
            "AND t.createdAt BETWEEN :start AND :end")
    Double sumCompletedRevenue(@Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.status = com.team27.amazon.billing.model.TransactionStatus.COMPLETED " +
            "AND t.createdAt BETWEEN :start AND :end")
    Long countCompleted(@Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM Transaction t " +
            "WHERE t.status = com.team27.amazon.billing.model.TransactionStatus.REFUNDED " +
            "AND t.createdAt BETWEEN :start AND :end")
    Double sumRefundedAmount(@Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.status = com.team27.amazon.billing.model.TransactionStatus.REFUNDED " +
            "AND t.createdAt BETWEEN :start AND :end")
    Long countRefunded(@Param("start") LocalDateTime start,
                       @Param("end") LocalDateTime end);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.transactionVouchers tv " +
            "LEFT JOIN FETCH tv.voucher WHERE t.id = :id")
    Optional<Transaction> findByIdWithVouchers(@Param("id") Long id);

    @Query(value = "SELECT user_id FROM orders WHERE id = :orderId", nativeQuery = true)
    Long findUserIdByOrderId(@Param("orderId") Long orderId);

    // ── S5-F10: category revenue aggregation ─────────────────────────────
    @Query(value = """
        SELECT
            p.category,
            SUM(oi.quantity * oi.price_at_purchase)                        AS grossRevenue,
            COUNT(DISTINCT t.id)                                            AS transactionCount,
            COUNT(DISTINCT CASE WHEN t.status::text = 'REFUNDED' THEN t.id END) AS refundCount
        FROM transactions t
        JOIN orders o       ON o.id = t.order_id
        JOIN order_items oi ON oi.order_id = o.id
        JOIN products p     ON p.id = oi.product_id
        WHERE o.ordered_at BETWEEN :startDate AND :endDate
          AND t.status::text IN ('COMPLETED','REFUNDED')
        GROUP BY p.category
        """, nativeQuery = true)
    List<Object[]> getCategoryRevenue(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ── S5-F10: fetch REFUNDED transactions with transactionDetails for partial refund calc
    @Query(value = """
        SELECT t.id, t.transaction_details, t.amount, p.category
        FROM transactions t
        JOIN orders o       ON o.id = t.order_id
        JOIN order_items oi ON oi.order_id = o.id
        JOIN products p     ON p.id = oi.product_id
        WHERE o.ordered_at BETWEEN :startDate AND :endDate
          AND t.status::text = 'REFUNDED'
        """, nativeQuery = true)
    List<Object[]> getRefundedTransactionDetails(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ── S5-F11: get shipment IDs for an order ─────────────────────────────
    @Query(value = "SELECT id FROM shipments WHERE order_id = :orderId", nativeQuery = true)
    List<Long> findShipmentIdsByOrderId(@Param("orderId") Long orderId);

    // ── S5-F12: get order items for a transaction's order ─────────────────
    @Query(value = """
        SELECT oi.id, oi.price_at_purchase, oi.quantity, oi.product_id
        FROM order_items oi
        JOIN transactions t ON t.order_id = oi.order_id
        WHERE t.id = :transactionId
        """, nativeQuery = true)
    List<Object[]> getOrderItemsByTransactionId(@Param("transactionId") Long transactionId);

    // ── S5-F12: validate that orderItemIds belong to this transaction's order
    @Query(value = """
        SELECT COUNT(*) FROM order_items oi
        JOIN transactions t ON t.order_id = oi.order_id
        WHERE t.id = :transactionId AND oi.id = :orderItemId
        """, nativeQuery = true)
    int countOrderItemBelongsToTransaction(
            @Param("transactionId") Long transactionId,
            @Param("orderItemId") Long orderItemId
    );

    @Query(value = """
    SELECT SUM(oi.quantity * oi.price_at_purchase)
    FROM transactions t
    JOIN orders o ON o.id = t.order_id
    JOIN order_items oi ON oi.order_id = o.id
    JOIN products p ON p.id = oi.product_id
    WHERE t.status::text = 'REFUNDED'
    AND p.category = :category
    AND o.ordered_at BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    Double getRefundedRevenueByCategory(
            @Param("category") String category,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = "SELECT id FROM order_items WHERE order_id = :orderId", nativeQuery = true)
    List<Long> findOrderItemIdsByOrderId(@Param("orderId") Long orderId);

    @Query(value = "SELECT COALESCE(SUM(price_at_purchase * quantity), 0) FROM order_items WHERE id IN (:ids)", nativeQuery = true)
    double sumPriceForItems(@Param("ids") List<Long> ids);

    @Query(value = "SELECT COUNT(*) FROM order_items WHERE id IN (:ids) AND order_id = :orderId", nativeQuery = true)
    int countItemsBelongingToOrder(@Param("ids") List<Long> ids, @Param("orderId") Long orderId);


}
