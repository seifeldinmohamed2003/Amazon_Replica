package com.team27.amazon.product.dto;

public class ProductSalesDTO {

    private Long productId;
    private String name;
    private Long totalUnitsSold;
    private Double totalRevenue;
    private Double averageSellingPrice;

    public ProductSalesDTO() {
    }

    public ProductSalesDTO(Long productId, String name, Long totalUnitsSold,
                           Double totalRevenue, Double averageSellingPrice) {
        this.productId = productId;
        this.name = name;
        this.totalUnitsSold = totalUnitsSold;
        this.totalRevenue = totalRevenue;
        this.averageSellingPrice = averageSellingPrice;
    }

    // ✅ ADD ONLY
    public static Builder builder() {
        return new Builder();
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getTotalUnitsSold() { return totalUnitsSold; }
    public void setTotalUnitsSold(Long totalUnitsSold) { this.totalUnitsSold = totalUnitsSold; }

    public Double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Double totalRevenue) { this.totalRevenue = totalRevenue; }

    public Double getAverageSellingPrice() { return averageSellingPrice; }
    public void setAverageSellingPrice(Double averageSellingPrice) { this.averageSellingPrice = averageSellingPrice; }

    // ✅ ADD ONLY
    public static class Builder {
        private Long productId;
        private String name;
        private Long totalUnitsSold;
        private Double totalRevenue;
        private Double averageSellingPrice;

        public Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder totalUnitsSold(Long totalUnitsSold) {
            this.totalUnitsSold = totalUnitsSold;
            return this;
        }

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder averageSellingPrice(Double averageSellingPrice) {
            this.averageSellingPrice = averageSellingPrice;
            return this;
        }

        public ProductSalesDTO build() {
            return new ProductSalesDTO(
                    productId,
                    name,
                    totalUnitsSold,
                    totalRevenue,
                    averageSellingPrice
            );
        }
    }
}