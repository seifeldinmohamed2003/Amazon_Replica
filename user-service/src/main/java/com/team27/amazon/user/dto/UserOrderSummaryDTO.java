package com.team27.amazon.user.dto;

public class UserOrderSummaryDTO {
    private Long userId;
    private String name;
    private Long totalOrders;
    private Long completedOrders;
    private Long cancelledOrders;
    private Double totalSpent;
    private Double averageOrderValue;

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

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Long getTotalOrders() { return totalOrders; }
    public Long getCompletedOrders() { return completedOrders; }
    public Long getCancelledOrders() { return cancelledOrders; }
    public Double getTotalSpent() { return totalSpent; }
    public Double getAverageOrderValue() { return averageOrderValue; }
}