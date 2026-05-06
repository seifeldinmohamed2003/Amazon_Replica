package com.team27.amazon.shipping.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ShippingAnalyticsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private long totalShipments;
    private double averageDeliveryTimeDays;
    private double onTimeRate;
    private Map<String, Long> shipmentsByStatus = new HashMap<>();
    private double averageAttempts;

    public ShippingAnalyticsDTO() {
    }

    private ShippingAnalyticsDTO(Builder builder) {
        this.totalShipments = builder.totalShipments;
        this.averageDeliveryTimeDays = builder.averageDeliveryTimeDays;
        this.onTimeRate = builder.onTimeRate;
        this.shipmentsByStatus = builder.shipmentsByStatus == null ? new HashMap<>() : builder.shipmentsByStatus;
        this.averageAttempts = builder.averageAttempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTotalShipments() {
        return totalShipments;
    }

    public void setTotalShipments(long totalShipments) {
        this.totalShipments = totalShipments;
    }

    public double getAverageDeliveryTimeDays() {
        return averageDeliveryTimeDays;
    }

    public void setAverageDeliveryTimeDays(double averageDeliveryTimeDays) {
        this.averageDeliveryTimeDays = averageDeliveryTimeDays;
    }

    public double getOnTimeRate() {
        return onTimeRate;
    }

    public void setOnTimeRate(double onTimeRate) {
        this.onTimeRate = onTimeRate;
    }

    public Map<String, Long> getShipmentsByStatus() {
        return shipmentsByStatus;
    }

    public void setShipmentsByStatus(Map<String, Long> shipmentsByStatus) {
        this.shipmentsByStatus = shipmentsByStatus == null ? new HashMap<>() : shipmentsByStatus;
    }

    public double getAverageAttempts() {
        return averageAttempts;
    }

    public void setAverageAttempts(double averageAttempts) {
        this.averageAttempts = averageAttempts;
    }

    public static class Builder {
        private long totalShipments;
        private double averageDeliveryTimeDays;
        private double onTimeRate;
        private Map<String, Long> shipmentsByStatus;
        private double averageAttempts;

        public Builder totalShipments(long totalShipments) {
            this.totalShipments = totalShipments;
            return this;
        }

        public Builder averageDeliveryTimeDays(double averageDeliveryTimeDays) {
            this.averageDeliveryTimeDays = averageDeliveryTimeDays;
            return this;
        }

        public Builder onTimeRate(double onTimeRate) {
            this.onTimeRate = onTimeRate;
            return this;
        }

        public Builder shipmentsByStatus(Map<String, Long> shipmentsByStatus) {
            this.shipmentsByStatus = shipmentsByStatus;
            return this;
        }

        public Builder averageAttempts(double averageAttempts) {
            this.averageAttempts = averageAttempts;
            return this;
        }

        public ShippingAnalyticsDTO build() {
            return new ShippingAnalyticsDTO(this);
        }
    }
}
