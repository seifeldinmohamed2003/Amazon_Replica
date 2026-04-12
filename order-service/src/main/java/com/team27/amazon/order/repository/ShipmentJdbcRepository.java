package com.team27.amazon.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Repository
public class ShipmentJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShipmentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByOrderId(Long orderId) {
        String sql = "SELECT COUNT(*) FROM shipments WHERE order_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, orderId);
        return count != null && count > 0;
    }

    public int markDeliveredByOrderId(Long orderId, LocalDate actualDelivery, LocalDateTime lastUpdate) {
        String sql = """
                UPDATE shipments
                SET status = ?, actual_delivery = ?, last_update = ?
                WHERE order_id = ?
                """;
        return jdbcTemplate.update(
                sql,
                "DELIVERED",
                actualDelivery,
                lastUpdate,
                orderId
        );
    }
}
