package com.team27.amazon.contracts.dto;

public record OrderSummaryDTO(
        long totalOrders,
        long completedOrders,
        long cancelledOrders,
        double totalSpent,
        double averageOrderValue
) {
    public static OrderSummaryDTO empty() {
        return new OrderSummaryDTO(0, 0, 0, 0.0, 0.0);
    }
}
