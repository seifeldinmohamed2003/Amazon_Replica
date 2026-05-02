package com.team27.amazon.billing.dto;

public class CategoryRevenueDTO {
    private String categoryName;
    private Double netRevenue;

    // 1. Private constructor for the Builder
    private CategoryRevenueDTO(Builder builder) {
        this.categoryName = builder.categoryName;
        this.netRevenue = builder.netRevenue;
    }

    // 2. Standard Getters
    public String getCategoryName() { return categoryName; }
    public Double getNetRevenue() { return netRevenue; }

    // 3. Static method to initialize the builder
    public static Builder builder() {
        return new Builder();
    }

    // 4. The Builder Inner Class
    public static class Builder {
        private String categoryName;
        private Double netRevenue;

        public Builder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public Builder netRevenue(Double netRevenue) {
            this.netRevenue = netRevenue;
            return this;
        }

        public CategoryRevenueDTO build() {
            return new CategoryRevenueDTO(this);
        }
    }
}