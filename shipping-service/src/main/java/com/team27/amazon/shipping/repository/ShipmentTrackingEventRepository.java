package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;
import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEventKey;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface ShipmentTrackingEventRepository
        extends CassandraRepository<ShipmentTrackingEvent, ShipmentTrackingEventKey> {
}