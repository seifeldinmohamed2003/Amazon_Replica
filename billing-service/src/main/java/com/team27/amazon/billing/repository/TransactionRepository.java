package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(value = """
        SELECT * FROM transactions
        WHERE (:status IS NULL OR status = :status)
        AND created_at BETWEEN :startDate AND :endDate
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Transaction> searchTransactions(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
    @Query("SELECT t.method, COUNT(t), SUM(t.amount) " +
            "FROM Transaction t " +
            "WHERE t.userId = :userId AND t.status = 'COMPLETED' " +
            "GROUP BY t.method")
    List<Object[]> getUserTransactionSummary(@Param("userId") Long userId);


    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    int countUserById(@Param("userId") Long userId);

    @Query(value = """
    SELECT method, COUNT(*) as cnt, SUM(amount) as total
    FROM transactions
    WHERE user_id = :userId AND status = 'COMPLETED'
    GROUP BY method
    """, nativeQuery = true)
    List<Object[]> getTransactionSummaryByUser(@Param("userId") Long userId);
}

