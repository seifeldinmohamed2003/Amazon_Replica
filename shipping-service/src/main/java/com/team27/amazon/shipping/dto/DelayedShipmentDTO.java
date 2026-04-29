package com.team27.amazon.shipping.dto;

import java.time.LocalDate;

public class DelayedShipmentDTO {

    private Long shipmentId;
    private Long orderId;
    private String carrier;
    private String trackingNumber;
    private LocalDate estimatedDelivery;
    private long daysOverdue;
    private int deliveryAttempts;

    public DelayedShipmentDTO() {
    }

    public DelayedShipmentDTO(Long shipmentId,
                              Long orderId,
                              String carrier,
                              String trackingNumber,
                              LocalDate estimatedDelivery,
                              long daysOverdue,
                              int deliveryAttempts) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.estimatedDelivery = estimatedDelivery;
        this.daysOverdue = daysOverdue;
        this.deliveryAttempts = deliveryAttempts;
    }

    public static Builder builder() {
        return new Builder();
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

    public LocalDate getEstimatedDelivery() {
        return estimatedDelivery;
    }

    public void setEstimatedDelivery(LocalDate estimatedDelivery) {
        this.estimatedDelivery = estimatedDelivery;
    }

    public long getDaysOverdue() {
        return daysOverdue;
    }

    public void setDaysOverdue(long daysOverdue) {
        this.daysOverdue = daysOverdue;
    }

    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }

    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public static class Builder {
        private Long shipmentId;
        private Long orderId;
        private String carrier;
        private String trackingNumber;
        private LocalDate estimatedDelivery;
        private long daysOverdue;
        private int deliveryAttempts;

        public Builder shipmentId(Long shipmentId) {
            this.shipmentId = shipmentId;
            return this;
        }

        public Builder orderId(Long orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder carrier(String carrier) {
            this.carrier = carrier;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder estimatedDelivery(LocalDate estimatedDelivery) {
            this.estimatedDelivery = estimatedDelivery;
            return this;
        }

        public Builder daysOverdue(long daysOverdue) {
            this.daysOverdue = daysOverdue;
            return this;
        }

        public Builder deliveryAttempts(int deliveryAttempts) {
            this.deliveryAttempts = deliveryAttempts;
            return this;
        }

        public DelayedShipmentDTO build() {
            return new DelayedShipmentDTO(
                    shipmentId,
                    orderId,
                    carrier,
                    trackingNumber,
                    estimatedDelivery,
                    daysOverdue,
                    deliveryAttempts
            );
        }
    }
}