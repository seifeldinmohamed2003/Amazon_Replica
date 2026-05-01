package com.team27.amazon.order.dto;

import java.util.HashMap;
import java.util.Map;

public class OrderAnalyticsDashboardDTO {

    private long totalOrders;
    private double totalRevenue;
    private double averageOrderValue;
    private double completionRate; // fraction (0..1)
    private Map<String, Long> ordersByStatus = new HashMap<>();

    public OrderAnalyticsDashboardDTO() {}

    public OrderAnalyticsDashboardDTO(long totalOrders, double totalRevenue, double averageOrderValue, double completionRate, Map<String, Long> ordersByStatus) {
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.averageOrderValue = averageOrderValue;
        this.completionRate = completionRate;
        this.ordersByStatus = ordersByStatus == null ? new HashMap<>() : new HashMap<>(ordersByStatus);
    }

    public static Builder builder() { return new Builder(); }

    public long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(long totalOrders) { this.totalOrders = totalOrders; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public double getAverageOrderValue() { return averageOrderValue; }
    public void setAverageOrderValue(double averageOrderValue) { this.averageOrderValue = averageOrderValue; }

    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }

    public Map<String, Long> getOrdersByStatus() { return ordersByStatus; }
    public void setOrdersByStatus(Map<String, Long> ordersByStatus) { this.ordersByStatus = ordersByStatus; }

    public static class Builder {
        private long totalOrders;
        private double totalRevenue;
        private double averageOrderValue;
        private double completionRate;
        private Map<String, Long> ordersByStatus = new HashMap<>();

        public Builder totalOrders(long totalOrders) { this.totalOrders = totalOrders; return this; }
        public Builder totalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; return this; }
        public Builder averageOrderValue(double averageOrderValue) { this.averageOrderValue = averageOrderValue; return this; }
        public Builder completionRate(double completionRate) { this.completionRate = completionRate; return this; }
        public Builder ordersByStatus(Map<String, Long> ordersByStatus) { this.ordersByStatus = ordersByStatus == null ? new HashMap<>() : new HashMap<>(ordersByStatus); return this; }

        public OrderAnalyticsDashboardDTO build() {
            return new OrderAnalyticsDashboardDTO(totalOrders, totalRevenue, averageOrderValue, completionRate, ordersByStatus);
        }
    }
}
