package com.team27.amazon.product.exception;

public class ProductReviewNotFoundException extends RuntimeException {

    public ProductReviewNotFoundException(Long reviewId) {
        super("Product review not found with id: " + reviewId);
    }
}