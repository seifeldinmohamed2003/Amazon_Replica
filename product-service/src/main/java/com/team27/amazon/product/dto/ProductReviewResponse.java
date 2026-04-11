package com.team27.amazon.product.dto;

import com.team27.amazon.product.model.ProductReview;

import java.time.LocalDateTime;
import java.util.Map;

public class ProductReviewResponse {

    private Long id;
    private Long userId;
    private Integer rating;
    private String title;
    private String comment;
    private Boolean verified;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private Long productId;

    public static ProductReviewResponse from(ProductReview review) {
        ProductReviewResponse response = new ProductReviewResponse();
        response.setId(review.getId());
        response.setUserId(review.getUserId());
        response.setRating(review.getRating());
        response.setTitle(review.getTitle());
        response.setComment(review.getComment());
        response.setVerified(review.getVerified());
        response.setMetadata(review.getMetadata());
        response.setCreatedAt(review.getCreatedAt());
        response.setProductId(review.getProduct() == null ? null : review.getProduct().getId());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}