package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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