package com.team27.amazon.shipping.dto.event;

public class ShipmentEvent {

    private Long shipmentId;

    private Long orderId;

    private String eventType;

    private String status;

    public ShipmentEvent() {
    }

    public ShipmentEvent(
            Long shipmentId,
            Long orderId,
            String eventType,
            String status
    ) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.status = status;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
