package com.team27.amazon.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProductJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByProductId(Long productId) {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, productId);
        return count != null && count > 0;
    }

    public Integer findStockQuantityByProductId(Long productId) {
        String sql = "SELECT stock_quantity FROM products WHERE id = ? FOR UPDATE";
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getInt("stock_quantity") : null, productId);
    }

    public int deductStockQuantity(Long productId, Integer quantity) {
        String sql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?";
        return jdbcTemplate.update(sql, quantity, productId, quantity);
    }
}
