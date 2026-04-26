package com.team27.amazon.billing.mongo.repository;

import com.team27.amazon.billing.mongo.model.ShipmentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipmentEventRepository extends MongoRepository<ShipmentEvent, String> {
    List<ShipmentEvent> findByShipmentIdInOrderByTimestampAsc(List<Long> shipmentIds);
}
