package com.team27.amazon.shipping.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;

@Repository
public interface ShipmentTrackingEventRepository
        extends CassandraRepository<ShipmentTrackingEvent, Long> {

    List<ShipmentTrackingEvent> findByShipmentIdOrderByTimestampDesc(Long shipmentId);

    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampBetweenOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampAfterOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime startTime
    );

    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampBeforeOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime endTime
    );
}