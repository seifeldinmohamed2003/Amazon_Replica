package com.team27.amazon.shipping.repository;

import com.team27.amazon.common.events.ShipmentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShipmentEventRepository extends MongoRepository<ShipmentEvent, String> {
}