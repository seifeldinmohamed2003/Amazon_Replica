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
    private String alertMessage;

    public static LowStockAlertDTO from(Product product) {
        LowStockAlertDTO dto = new LowStockAlertDTO();
        dto.setProductId(product.getId());
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());
        dto.setCategory(product.getCategory());
        dto.setStockQuantity(product.getStockQuantity());
        dto.setStatus(product.getStatus());
        dto.setRating(product.getRating());
        dto.setTotalRatings(product.getTotalRatings());

        int reviewCount = product.getProductReviews() == null ? 0 : product.getProductReviews().size();
        int verifiedReviewCount = 0;

        if (product.getProductReviews() != null) {
            for (ProductReview review : product.getProductReviews()) {
                if (Boolean.TRUE.equals(review.getVerified())) {
                    verifiedReviewCount++;
                }
            }
        }

        dto.setReviewCount(reviewCount);
        dto.setVerifiedReviewCount(verifiedReviewCount);
        dto.setAlertMessage("Low stock alert: only " + product.getStockQuantity() + " item(s) left.");

        return dto;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
    }

    public Integer getVerifiedReviewCount() {
        return verifiedReviewCount;
    }

    public void setVerifiedReviewCount(Integer verifiedReviewCount) {
        this.verifiedReviewCount = verifiedReviewCount;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public void setAlertMessage(String alertMessage) {
        this.alertMessage = alertMessage;
    }
}