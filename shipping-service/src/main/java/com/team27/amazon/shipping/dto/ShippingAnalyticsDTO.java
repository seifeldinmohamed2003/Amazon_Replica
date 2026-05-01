package com.team27.amazon.shipping.dto;

import java.io.Serializable;
import java.util.Map;

public class ShippingAnalyticsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long totalShipments;
    private final double averageDeliveryTimeDays;
    private final double onTimeRate;
    private final Map<String, Long> shipmentsByStatus;
    private final double averageAttempts;

    private ShippingAnalyticsDTO(Builder builder) {
        this.totalShipments = builder.totalShipments;
        this.averageDeliveryTimeDays = builder.averageDeliveryTimeDays;
        this.onTimeRate = builder.onTimeRate;
        this.shipmentsByStatus = builder.shipmentsByStatus;
        this.averageAttempts = builder.averageAttempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTotalShipments() {
        return totalShipments;
    }

    public double getAverageDeliveryTimeDays() {
        return averageDeliveryTimeDays;
    }

    public double getOnTimeRate() {
        return onTimeRate;
    }

    public Map<String, Long> getShipmentsByStatus() {
        return shipmentsByStatus;
    }

    public double getAverageAttempts() {
        return averageAttempts;
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
