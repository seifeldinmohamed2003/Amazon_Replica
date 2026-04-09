package com.team27.amazon.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class TransactionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertPendingTransaction(Long orderId, Long userId, Double amount, LocalDateTime createdAt) {
        String sql = """
                INSERT INTO transactions (order_id, user_id, amount, method, status, created_at, transaction_details)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """;

        String emptyJson = "{}";

        return jdbcTemplate.update(
                sql,
                orderId,
                userId,
                amount,
                null,
                "PENDING",
                createdAt,
                emptyJson
        );
    }
}
