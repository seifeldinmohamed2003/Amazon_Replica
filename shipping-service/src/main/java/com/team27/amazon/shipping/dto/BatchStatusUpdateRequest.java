package com.team27.amazon.shipping.dto;

import com.team27.amazon.shipping.model.ShipmentStatus;

public class BatchStatusUpdateRequest {
    private Long shipmentId;
    private ShipmentStatus status;
    private Double latitude;
    private Double longitude;

    public BatchStatusUpdateRequest() {
    }

    public BatchStatusUpdateRequest(Long shipmentId, ShipmentStatus status, Double latitude, Double longitude) {
        this.shipmentId = shipmentId;
        this.status = status;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(Long shipmentId) {
        this.shipmentId = shipmentId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
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
}