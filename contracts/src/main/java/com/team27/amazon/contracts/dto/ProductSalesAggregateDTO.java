package com.team27.amazon.contracts.dto;

public record ProductSalesAggregateDTO(
        long totalUnitsSold,
        double totalRevenue,
        double averageSellingPrice
) {}
