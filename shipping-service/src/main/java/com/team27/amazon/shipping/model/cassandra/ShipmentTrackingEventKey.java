package com.team27.amazon.shipping.model.cassandra;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.LocalDateTime;

@PrimaryKeyClass
public class ShipmentTrackingEventKey implements Serializable {

    @PrimaryKeyColumn(name = "shipment_id", type = PrimaryKeyType.PARTITIONED)
    private Long shipmentId;

    @PrimaryKeyColumn(name = "timestamp", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private LocalDateTime timestamp;

    public ShipmentTrackingEventKey() {}

    public ShipmentTrackingEventKey(Long shipmentId, LocalDateTime timestamp) {
        this.shipmentId = shipmentId;
        this.timestamp = timestamp;
    }

    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}