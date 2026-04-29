package com.team27.amazon.order.dto;

public class OrderAnalyticsDTO {

    private long totalOrders;
    private long deliveredOrders;
    private long cancelledOrders;
    private double totalRevenue;
    private double averageOrderValue;
    private double completionRate;

    public OrderAnalyticsDTO() {
    }

    public OrderAnalyticsDTO(long totalOrders, long deliveredOrders, long cancelledOrders,
                             double totalRevenue, double averageOrderValue, double completionRate) {
        this.totalOrders = totalOrders;
        this.deliveredOrders = deliveredOrders;
        this.cancelledOrders = cancelledOrders;
        this.totalRevenue = totalRevenue;
        this.averageOrderValue = averageOrderValue;
        this.completionRate = completionRate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public long getDeliveredOrders() {
        return deliveredOrders;
    }

    public void setDeliveredOrders(long deliveredOrders) {
        this.deliveredOrders = deliveredOrders;
    }

    public long getCancelledOrders() {
        return cancelledOrders;
    }

    public void setCancelledOrders(long cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public double getAverageOrderValue() {
        return averageOrderValue;
    }

    public void setAverageOrderValue(double averageOrderValue) {
        this.averageOrderValue = averageOrderValue;
    }

    public double getCompletionRate() {
        return completionRate;
    }

    public void setCompletionRate(double completionRate) {
        this.completionRate = completionRate;
    }

    public static class Builder {
        private long totalOrders;
        private long deliveredOrders;
        private long cancelledOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private double completionRate;

        public Builder totalOrders(long totalOrders) {
            this.totalOrders = totalOrders;
            return this;
        }

        public Builder deliveredOrders(long deliveredOrders) {
            this.deliveredOrders = deliveredOrders;
            return this;
        }

        public Builder cancelledOrders(long cancelledOrders) {
            this.cancelledOrders = cancelledOrders;
            return this;
        }

        public Builder totalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageOrderValue(double averageOrderValue) {
            this.averageOrderValue = averageOrderValue;
            return this;
        }

        public Builder completionRate(double completionRate) {
            this.completionRate = completionRate;
            return this;
        }

        public OrderAnalyticsDTO build() {
            return new OrderAnalyticsDTO(
                    totalOrders,
                    deliveredOrders,
                    cancelledOrders,
                    totalRevenue,
                    averageOrderValue,
                    completionRate
            );
        }
    }
}