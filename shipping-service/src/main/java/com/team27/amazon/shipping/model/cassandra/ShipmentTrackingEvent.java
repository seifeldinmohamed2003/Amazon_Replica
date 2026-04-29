package com.team27.amazon.shipping.model.cassandra;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("shipment_tracking_events")
public class ShipmentTrackingEvent {

    @PrimaryKey
    private ShipmentTrackingEventKey key;

    private String status;
    private String carrier;

    @Column("tracking_number")
    private String trackingNumber;

    private Double latitude;
    private Double longitude;
    private String notes;

    public ShipmentTrackingEvent() {}

    public ShipmentTrackingEvent(
            ShipmentTrackingEventKey key,
            String status,
            String carrier,
            String trackingNumber,
            Double latitude,
            Double longitude,
            String notes
    ) {
        this.key = key;
        this.status = status;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.notes = notes;
    }

    public ShipmentTrackingEventKey getKey() { return key; }
    public void setKey(ShipmentTrackingEventKey key) { this.key = key; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}