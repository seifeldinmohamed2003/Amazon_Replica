package com.team27.amazon.order.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShippingAddressJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public ShippingAddressJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByShippingAddressId(Long shippingAddressId) {
        String sql = "SELECT COUNT(*) FROM shipping_addresses WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, shippingAddressId);
        return count != null && count > 0;
    }
}
