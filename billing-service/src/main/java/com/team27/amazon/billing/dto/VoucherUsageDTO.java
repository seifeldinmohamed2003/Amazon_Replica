package com.team27.amazon.billing.dto;

public class VoucherUsageDTO {

    private Long voucherId;
    private String code;
    private String discountType;
    private Double discountValue;
    private Integer timesUsed;
    private Double totalDiscountGiven;
    private Boolean active;
    private Boolean expired;

    public VoucherUsageDTO() {}

    public VoucherUsageDTO(Long voucherId, String code, String discountType, Double discountValue,
                           Integer timesUsed, Double totalDiscountGiven, Boolean active, Boolean expired) {
        this.voucherId = voucherId;
        this.code = code;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.timesUsed = timesUsed;
        this.totalDiscountGiven = totalDiscountGiven;
        this.active = active;
        this.expired = expired;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long v) { this.voucherId = v; }

    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String v) { this.discountType = v; }

    public Double getDiscountValue() { return discountValue; }
    public void setDiscountValue(Double v) { this.discountValue = v; }

    public Integer getTimesUsed() { return timesUsed; }
    public void setTimesUsed(Integer v) { this.timesUsed = v; }

    public Double getTotalDiscountGiven() { return totalDiscountGiven; }
    public void setTotalDiscountGiven(Double v) { this.totalDiscountGiven = v; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }

    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean v) { this.expired = v; }

    public static class Builder {
        private Long voucherId;
        private String code;
        private String discountType;
        private Double discountValue;
        private Integer timesUsed;
        private Double totalDiscountGiven;
        private Boolean active;
        private Boolean expired;

        public Builder voucherId(Long voucherId) {
            this.voucherId = voucherId;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder discountType(String discountType) {
            this.discountType = discountType;
            return this;
        }

        public Builder discountValue(Double discountValue) {
            this.discountValue = discountValue;
            return this;
        }

        public Builder timesUsed(Integer timesUsed) {
            this.timesUsed = timesUsed;
            return this;
        }

        public Builder totalDiscountGiven(Double totalDiscountGiven) {
            this.totalDiscountGiven = totalDiscountGiven;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder expired(Boolean expired) {
            this.expired = expired;
            return this;
        }

        public VoucherUsageDTO build() {
            return new VoucherUsageDTO(
                    voucherId,
                    code,
                    discountType,
                    discountValue,
                    timesUsed,
                    totalDiscountGiven,
                    active,
                    expired
            );
        }
    }
}