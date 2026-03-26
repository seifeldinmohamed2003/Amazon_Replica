package com.team27.amazon.order.repository;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);

    List<Order> findByOrderedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<Order> findByShippingAddressId(Long shippingAddressId);
}

