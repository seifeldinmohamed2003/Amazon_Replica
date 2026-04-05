package com.team27.amazon.shipping.dto;

public class CarrierSummaryDTO {

    private String carrier;
    private long totalShipments;
    private long deliveredCount;
    private double averageDeliveryDays;
    private double onTimeRate;

    public CarrierSummaryDTO() {}

    public CarrierSummaryDTO(String carrier,
                             long totalShipments,
                             long deliveredCount,
                             double averageDeliveryDays,
                             double onTimeRate) {
        this.carrier = carrier;
        this.totalShipments = totalShipments;
        this.deliveredCount = deliveredCount;
        this.averageDeliveryDays = averageDeliveryDays;
        this.onTimeRate = onTimeRate;
    }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public long getTotalShipments() { return totalShipments; }
    public void setTotalShipments(long totalShipments) { this.totalShipments = totalShipments; }

    public long getDeliveredCount() { return deliveredCount; }
    public void setDeliveredCount(long deliveredCount) { this.deliveredCount = deliveredCount; }

    public double getAverageDeliveryDays() { return averageDeliveryDays; }
    public void setAverageDeliveryDays(double averageDeliveryDays) { this.averageDeliveryDays = averageDeliveryDays; }

    public double getOnTimeRate() { return onTimeRate; }
    public void setOnTimeRate(double onTimeRate) { this.onTimeRate = onTimeRate; }
}