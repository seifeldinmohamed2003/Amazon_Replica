package com.team27.amazon.billing.dto;

public class CategoryRevenueDTO {
    private String categoryName;
    private Double grossRevenue;
    private Double refundedRevenue;
    private Double netRevenue;
    private Long transactionCount;
    private Long refundCount;

    // 1. Private constructor for the Builder
    private CategoryRevenueDTO(Builder builder) {
        this.categoryName = builder.categoryName;
        this.grossRevenue = builder.grossRevenue;
        this.refundedRevenue = builder.refundedRevenue;
        this.netRevenue = builder.netRevenue;
        this.transactionCount = builder.transactionCount;
        this.refundCount = builder.refundCount;
    }

    // 2. Standard Getters
    public String getCategoryName() { return categoryName; }
    public Double getGrossRevenue() { return grossRevenue; }
    public Double getRefundedRevenue() { return refundedRevenue; }
    public Double getNetRevenue() { return netRevenue; }
    public Long getTransactionCount() { return transactionCount; }
    public Long getRefundCount() { return refundCount; }

    // 3. Static method to initialize the builder
    public static Builder builder() {
        return new Builder();
    }

    // 4. The Builder Inner Class
    public static class Builder {
        private String categoryName;
        private Double grossRevenue;
        private Double refundedRevenue;
        private Double netRevenue;
        private Long transactionCount;
        private Long refundCount;

        public Builder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public Builder grossRevenue(Double grossRevenue) {
            this.grossRevenue = grossRevenue;
            return this;
        }

        public Builder refundedRevenue(Double refundedRevenue) {
            this.refundedRevenue = refundedRevenue;
            return this;
        }

        public Builder netRevenue(Double netRevenue) {
            this.netRevenue = netRevenue;
            return this;
        }

        public Builder transactionCount(Long transactionCount) {
            this.transactionCount = transactionCount;
            return this;
        }

        public Builder refundCount(Long refundCount) {
            this.refundCount = refundCount;
            return this;
        }

        public CategoryRevenueDTO build() {
            return new CategoryRevenueDTO(this);
        }
    }
}