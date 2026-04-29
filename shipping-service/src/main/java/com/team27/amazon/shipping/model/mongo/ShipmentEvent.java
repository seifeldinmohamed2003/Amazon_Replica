package com.team27.amazon.shipping.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "shipment_events")
public class ShipmentEvent implements MongoEvent {

    @Id
    private String id;

    private Long shipmentId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public ShipmentEvent() {}

    public ShipmentEvent(Long shipmentId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.shipmentId = shipmentId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    @Override
    public String getId() { return id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getAction() { return action; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    // getters & setters
    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }

    public void setId(String id) { this.id = id; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}