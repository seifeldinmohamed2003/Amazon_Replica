package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;

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

}