package com.team27.amazon.billing.dto;

public class CategoryRevenueDTO {

    private String category;
    private Double grossRevenue;
    private Double refundedRevenue;
    private Double netRevenue;
    private Long transactionCount;
    private Long refundCount;
    private Double returnRate;

    private CategoryRevenueDTO() {}

    // ── Builder ────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CategoryRevenueDTO dto = new CategoryRevenueDTO();

        public Builder category(String v)          { dto.category = v;          return this; }
        public Builder grossRevenue(Double v)       { dto.grossRevenue = v;       return this; }
        public Builder refundedRevenue(Double v)    { dto.refundedRevenue = v;    return this; }
        public Builder netRevenue(Double v)         { dto.netRevenue = v;         return this; }
        public Builder transactionCount(Long v)     { dto.transactionCount = v;   return this; }
        public Builder refundCount(Long v)          { dto.refundCount = v;        return this; }
        public Builder returnRate(Double v)         { dto.returnRate = v;         return this; }

        public CategoryRevenueDTO build() { return dto; }
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public String  getCategory()         { return category; }
    public Double  getGrossRevenue()     { return grossRevenue; }
    public Double  getRefundedRevenue()  { return refundedRevenue; }
    public Double  getNetRevenue()       { return netRevenue; }
    public Long    getTransactionCount() { return transactionCount; }
    public Long    getRefundCount()      { return refundCount; }
    public Double  getReturnRate()       { return returnRate; }
}
