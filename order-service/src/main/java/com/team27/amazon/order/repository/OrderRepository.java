package com.team27.amazon.order.repository;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.team27.amazon.order.dto.OrderAnalyticsDTO;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    List<Order> findByOrderedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Order> findByShippingAddressId(Long shippingAddressId);
    @Query(value = """
        SELECT *
        FROM orders
        WHERE metadata ->> :key = :value
        """, nativeQuery = true)
    List<Order> findByMetadataField(@Param("key") String key, @Param("value") String value);
}

