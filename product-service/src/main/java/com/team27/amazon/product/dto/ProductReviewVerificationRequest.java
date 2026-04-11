package com.team27.amazon.product.dto;

import jakarta.validation.constraints.NotNull;

public class ProductReviewVerificationRequest {

    @NotNull
    private Long verifiedBy;

    public Long getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Long verifiedBy) {
        this.verifiedBy = verifiedBy;
    }
}