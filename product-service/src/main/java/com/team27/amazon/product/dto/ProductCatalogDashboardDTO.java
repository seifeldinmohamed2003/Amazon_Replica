package com.team27.amazon.product.dto;

import java.util.HashMap;
import java.util.Map;

public class ProductCatalogDashboardDTO {

    private Long totalProducts;
    private Long outOfStockCount;
    private Double averageRating;
    private Map<String, Long> categoryDistribution;
    private Double averagePrice;
    private Long lowStockCount;

    public ProductCatalogDashboardDTO() {
        this.categoryDistribution = new HashMap<>();
    }

    private ProductCatalogDashboardDTO(Builder builder) {
        this.totalProducts = builder.totalProducts;
        this.outOfStockCount = builder.outOfStockCount;
        this.averageRating = builder.averageRating;
        this.categoryDistribution = builder.categoryDistribution;
        this.averagePrice = builder.averagePrice;
        this.lowStockCount = builder.lowStockCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(Long totalProducts) {
        this.totalProducts = totalProducts;
    }

    public Long getOutOfStockCount() {
        return outOfStockCount;
    }

    public void setOutOfStockCount(Long outOfStockCount) {
        this.outOfStockCount = outOfStockCount;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public Map<String, Long> getCategoryDistribution() {
        return categoryDistribution;
    }

    public void setCategoryDistribution(Map<String, Long> categoryDistribution) {
        this.categoryDistribution = categoryDistribution;
    }

    public Double getAveragePrice() {
        return averagePrice;
    }

    public void setAveragePrice(Double averagePrice) {
        this.averagePrice = averagePrice;
    }

    public Long getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(Long lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public static class Builder {
        private Long totalProducts = 0L;
        private Long outOfStockCount = 0L;
        private Double averageRating = 0.0;
        private Map<String, Long> categoryDistribution = new HashMap<>();
        private Double averagePrice = 0.0;
        private Long lowStockCount = 0L;

        public Builder totalProducts(Long totalProducts) {
            this.totalProducts = totalProducts == null ? 0L : totalProducts;
            return this;
        }

        public Builder outOfStockCount(Long outOfStockCount) {
            this.outOfStockCount = outOfStockCount == null ? 0L : outOfStockCount;
            return this;
        }

        public Builder averageRating(Double averageRating) {
            this.averageRating = averageRating == null ? 0.0 : averageRating;
            return this;
        }

        public Builder categoryDistribution(Map<String, Long> categoryDistribution) {
            this.categoryDistribution = categoryDistribution == null ? new HashMap<>() : categoryDistribution;
            return this;
        }

        public Builder averagePrice(Double averagePrice) {
            this.averagePrice = averagePrice == null ? 0.0 : averagePrice;
            return this;
        }

        public Builder lowStockCount(Long lowStockCount) {
            this.lowStockCount = lowStockCount == null ? 0L : lowStockCount;
            return this;
        }

        public ProductCatalogDashboardDTO build() {
            return new ProductCatalogDashboardDTO(this);
        }
    }
}