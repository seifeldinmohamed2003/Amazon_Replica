package com.team27.amazon.user.dto;

public class UserOrderSummaryDTO {
    private Long userId;
    private String name;
    private Long totalOrders;
    private Long completedOrders;
    private Long cancelledOrders;
    private Double totalSpent;
    private Double averageOrderValue;

    public UserOrderSummaryDTO() {
    }

    public UserOrderSummaryDTO(Long userId, String name, Long totalOrders,
                               Long completedOrders, Long cancelledOrders,
                               Double totalSpent, Double averageOrderValue) {
        this.userId = userId;
        this.name = name;
        this.totalOrders = totalOrders;
        this.completedOrders = completedOrders;
        this.cancelledOrders = cancelledOrders;
        this.totalSpent = totalSpent;
        this.averageOrderValue = averageOrderValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Long getTotalOrders() { return totalOrders; }
    public Long getCompletedOrders() { return completedOrders; }
    public Long getCancelledOrders() { return cancelledOrders; }
    public Double getTotalSpent() { return totalSpent; }
    public Double getAverageOrderValue() { return averageOrderValue; }

    public static class Builder {
        private Long userId;
        private String name;
        private Long totalOrders;
        private Long completedOrders;
        private Long cancelledOrders;
        private Double totalSpent;
        private Double averageOrderValue;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder totalOrders(Long totalOrders) { this.totalOrders = totalOrders; return this; }
        public Builder completedOrders(Long completedOrders) { this.completedOrders = completedOrders; return this; }
        public Builder cancelledOrders(Long cancelledOrders) { this.cancelledOrders = cancelledOrders; return this; }
        public Builder totalSpent(Double totalSpent) { this.totalSpent = totalSpent; return this; }
        public Builder averageOrderValue(Double averageOrderValue) { this.averageOrderValue = averageOrderValue; return this; }

        public UserOrderSummaryDTO build() {
            return new UserOrderSummaryDTO(
                    userId, name, totalOrders,
                    completedOrders, cancelledOrders,
                    totalSpent, averageOrderValue
            );
        }
    }
}