package com.team27.amazon.shipping.repository;

import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;
import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEventKey;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.data.cassandra.repository.AllowFiltering;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cassandra repository for shipment tracking events (S4-F12).
 * Handles time-series tracking data with optional time range filtering.
 */
@Repository
public interface TrackingEventRepository extends CrudRepository<ShipmentTrackingEvent, ShipmentTrackingEventKey> {

    /**
     * Find all tracking events for a shipment, sorted by timestamp descending (most recent first).
     * The ordering is handled by Cassandra clustering order.
     */
    @Query("SELECT * FROM shipment_tracking_events WHERE shipment_id = ?0 ORDER BY timestamp DESC")
    List<ShipmentTrackingEvent> findByShipmentIdOrderByTimestampDesc(Long shipmentId);

    /**
     * Find tracking events for a shipment within a time range, sorted by timestamp descending.
     */
    @Query("SELECT * FROM shipment_tracking_events WHERE shipment_id = ?0 AND timestamp >= ?1 AND timestamp <= ?2 ORDER BY timestamp DESC")
    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampBetweenOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * Find tracking events for a shipment from a start time onwards.
     */
    @Query("SELECT * FROM shipment_tracking_events WHERE shipment_id = ?0 AND timestamp >= ?1 ORDER BY timestamp DESC")
    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampAfterOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime startTime
    );

    /**
     * Find tracking events for a shipment up to an end time.
     */
    @Query("SELECT * FROM shipment_tracking_events WHERE shipment_id = ?0 AND timestamp <= ?1 ORDER BY timestamp DESC")
    List<ShipmentTrackingEvent> findByShipmentIdAndTimestampBeforeOrderByTimestampDesc(
            Long shipmentId,
            LocalDateTime endTime
    );
}