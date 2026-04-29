package com.team27.amazon.shipping.dto;

public class NearbyShipmentDTO {
    private Long shipmentId;
    private Long orderId;
    private String carrier;
    private String trackingNumber;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;

    public NearbyShipmentDTO() {}

    public NearbyShipmentDTO(Long shipmentId, Long orderId, String carrier, String trackingNumber,
                             Double latitude, Double longitude, Double distanceKm) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getShipmentId() { return shipmentId; }
    public void setShipmentId(Long shipmentId) { this.shipmentId = shipmentId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public static class Builder {
        private Long shipmentId;
        private Long orderId;
        private String carrier;
        private String trackingNumber;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;

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

        public Builder latitude(Double latitude) {
            this.latitude = latitude;
            return this;
        }

        public Builder longitude(Double longitude) {
            this.longitude = longitude;
            return this;
        }

        public Builder distanceKm(Double distanceKm) {
            this.distanceKm = distanceKm;
            return this;
        }

        public NearbyShipmentDTO build() {
            return new NearbyShipmentDTO(
                    shipmentId,
                    orderId,
                    carrier,
                    trackingNumber,
                    latitude,
                    longitude,
                    distanceKm
            );
        }
    }
}