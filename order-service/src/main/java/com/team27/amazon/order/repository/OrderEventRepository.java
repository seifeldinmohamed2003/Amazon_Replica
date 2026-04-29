package com.team27.amazon.order.repository;

import com.team27.amazon.common.events.OrderEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderEventRepository extends MongoRepository<OrderEvent, String> {
}