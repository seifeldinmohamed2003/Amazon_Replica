package com.team27.amazon.shipping.model.cassandra;

import java.time.LocalDateTime;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("shipment_tracking_events")
public class ShipmentTrackingEvent {

    @PrimaryKeyColumn(
            name = "shipment_id",
            ordinal = 0,
            type = PrimaryKeyType.PARTITIONED
    )
    private Long shipmentId;

    @PrimaryKeyColumn(
            name = "timestamp",
            ordinal = 1,
            type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING
    )
    private LocalDateTime timestamp;

    @Column("status")
    private String status;

    @Column("carrier")
    private String carrier;

    @Column("tracking_number")
    private String trackingNumber;

    @Column("latitude")
    private Double latitude;

    @Column("longitude")
    private Double longitude;

    @Column("notes")
    private String notes;

    public ShipmentTrackingEvent() {
    }

    public ShipmentTrackingEvent(
            Long shipmentId,
            LocalDateTime timestamp,
            String status,
            String carrier,
            String trackingNumber,
            Double latitude,
            Double longitude,
            String notes
    ) {
        this.shipmentId = shipmentId;
        this.timestamp = timestamp;
        this.status = status;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notes = notes;
    }

    public ShipmentTrackingEvent(
            ShipmentTrackingEventKey key,
            String status,
            String carrier,
            String trackingNumber,
            Double latitude,
            Double longitude,
            String notes
    ) {
        this.shipmentId = key == null ? null : key.getShipmentId();
        this.timestamp = key == null ? null : key.getTimestamp();
        this.status = status;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notes = notes;
    }

    public ShipmentTrackingEventKey getKey() {
        return new ShipmentTrackingEventKey(this.shipmentId, this.timestamp);
    }

    public void setKey(ShipmentTrackingEventKey key) {
        this.shipmentId = key == null ? null : key.getShipmentId();
        this.timestamp = key == null ? null : key.getTimestamp();
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}