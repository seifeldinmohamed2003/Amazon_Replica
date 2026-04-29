package com.team27.amazon.product.dto;

import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.model.ProductStatus;

public class LowStockAlertDTO {

    private Long productId;
    private String name;
    private String brand;
    private String category;
    private Integer stockQuantity;
    private ProductStatus status;
    private Double rating;
    private Integer totalRatings;
    private Integer reviewCount;
    private Integer verifiedReviewCount;
    private Boolean hasNegativeReviews;
    private String alertMessage;

    public LowStockAlertDTO() {
    }

    public LowStockAlertDTO(Long productId, String name, String brand, String category,
                            Integer stockQuantity, ProductStatus status, Double rating,
                            Integer totalRatings, Integer reviewCount, Integer verifiedReviewCount,
                            Boolean hasNegativeReviews, String alertMessage) {
        this.productId = productId;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.status = status;
        this.rating = rating;
        this.totalRatings = totalRatings;
        this.reviewCount = reviewCount;
        this.verifiedReviewCount = verifiedReviewCount;
        this.hasNegativeReviews = hasNegativeReviews;
        this.alertMessage = alertMessage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LowStockAlertDTO from(Product product) {
        int reviewCount = product.getProductReviews() == null ? 0 : product.getProductReviews().size();
        int verifiedReviewCount = 0;
        boolean hasNegativeReviews = false;

        if (product.getProductReviews() != null) {
            for (ProductReview review : product.getProductReviews()) {
                if (Boolean.TRUE.equals(review.getVerified())) {
                    verifiedReviewCount++;
                }
                if (review.getRating() != null && review.getRating() <= 2) {
                    hasNegativeReviews = true;
                }
            }
        }

        return LowStockAlertDTO.builder()
                .productId(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .stockQuantity(product.getStockQuantity())
                .status(product.getStatus())
                .rating(product.getRating())
                .totalRatings(product.getTotalRatings())
                .reviewCount(reviewCount)
                .verifiedReviewCount(verifiedReviewCount)
                .hasNegativeReviews(hasNegativeReviews)
                .alertMessage("Low stock alert: only " + product.getStockQuantity() + " item(s) left.")
                .build();
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getTotalRatings() { return totalRatings; }
    public void setTotalRatings(Integer totalRatings) { this.totalRatings = totalRatings; }

    public Integer getReviewCount() { return reviewCount; }
    public void setReviewCount(Integer reviewCount) { this.reviewCount = reviewCount; }

    public Integer getVerifiedReviewCount() { return verifiedReviewCount; }
    public void setVerifiedReviewCount(Integer verifiedReviewCount) { this.verifiedReviewCount = verifiedReviewCount; }

    public Boolean getHasNegativeReviews() { return hasNegativeReviews; }
    public void setHasNegativeReviews(Boolean hasNegativeReviews) { this.hasNegativeReviews = hasNegativeReviews; }

    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }

    public static class Builder {
        private Long productId;
        private String name;
        private String brand;
        private String category;
        private Integer stockQuantity;
        private ProductStatus status;
        private Double rating;
        private Integer totalRatings;
        private Integer reviewCount;
        private Integer verifiedReviewCount;
        private Boolean hasNegativeReviews;
        private String alertMessage;

        public Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder stockQuantity(Integer stockQuantity) {
            this.stockQuantity = stockQuantity;
            return this;
        }

        public Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }

        public Builder rating(Double rating) {
            this.rating = rating;
            return this;
        }

        public Builder totalRatings(Integer totalRatings) {
            this.totalRatings = totalRatings;
            return this;
        }

        public Builder reviewCount(Integer reviewCount) {
            this.reviewCount = reviewCount;
            return this;
        }

        public Builder verifiedReviewCount(Integer verifiedReviewCount) {
            this.verifiedReviewCount = verifiedReviewCount;
            return this;
        }

        public Builder hasNegativeReviews(Boolean hasNegativeReviews) {
            this.hasNegativeReviews = hasNegativeReviews;
            return this;
        }

        public Builder alertMessage(String alertMessage) {
            this.alertMessage = alertMessage;
            return this;
        }

        public LowStockAlertDTO build() {
            return new LowStockAlertDTO(
                    productId,
                    name,
                    brand,
                    category,
                    stockQuantity,
                    status,
                    rating,
                    totalRatings,
                    reviewCount,
                    verifiedReviewCount,
                    hasNegativeReviews,
                    alertMessage
            );
        }
    }
}