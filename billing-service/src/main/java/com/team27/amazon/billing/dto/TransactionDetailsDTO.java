package com.team27.amazon.billing.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class TransactionDetailsDTO {

    private Long transactionId;
    private Long orderId;
    private Long userId;
    private Double originalAmount;
    private String method;
    private String status;
    private Map<String, Object> transactionDetails;
    private List<AppliedVoucherDTO> appliedVouchers;
    private Double totalDiscount;
    private Double finalAmount;

    public static class AppliedVoucherDTO {
        private String voucherCode;
        private String discountType;
        private Double discountApplied;
        private LocalDateTime appliedAt;

        public AppliedVoucherDTO() {}

        public AppliedVoucherDTO(String voucherCode, String discountType,
                                 Double discountApplied, LocalDateTime appliedAt) {
            this.voucherCode = voucherCode;
            this.discountType = discountType;
            this.discountApplied = discountApplied;
            this.appliedAt = appliedAt;
        }

        public String getVoucherCode()       { return voucherCode; }
        public void setVoucherCode(String v) { this.voucherCode = v; }
        public String getDiscountType()       { return discountType; }
        public void setDiscountType(String v) { this.discountType = v; }
        public Double getDiscountApplied()       { return discountApplied; }
        public void setDiscountApplied(Double v) { this.discountApplied = v; }
        public LocalDateTime getAppliedAt()          { return appliedAt; }
        public void setAppliedAt(LocalDateTime v)    { this.appliedAt = v; }
    }

    // ── Builder ──────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final TransactionDetailsDTO dto = new TransactionDetailsDTO();
        public Builder transactionId(Long v)                     { dto.transactionId = v;      return this; }
        public Builder orderId(Long v)                           { dto.orderId = v;            return this; }
        public Builder userId(Long v)                            { dto.userId = v;             return this; }
        public Builder originalAmount(Double v)                  { dto.originalAmount = v;     return this; }
        public Builder method(String v)                          { dto.method = v;             return this; }
        public Builder status(String v)                          { dto.status = v;             return this; }
        public Builder transactionDetails(Map<String,Object> v)  { dto.transactionDetails = v; return this; }
        public Builder appliedVouchers(List<AppliedVoucherDTO> v){ dto.appliedVouchers = v;   return this; }
        public Builder totalDiscount(Double v)                   { dto.totalDiscount = v;      return this; }
        public Builder finalAmount(Double v)                     { dto.finalAmount = v;        return this; }
        public TransactionDetailsDTO build()                     { return dto; }
    }

    public Long getTransactionId()                       { return transactionId; }
    public void setTransactionId(Long v)                 { this.transactionId = v; }
    public Long getOrderId()                             { return orderId; }
    public void setOrderId(Long v)                       { this.orderId = v; }
    public Long getUserId()                              { return userId; }
    public void setUserId(Long v)                        { this.userId = v; }
    public Double getOriginalAmount()                    { return originalAmount; }
    public void setOriginalAmount(Double v)              { this.originalAmount = v; }
    public String getMethod()                            { return method; }
    public void setMethod(String v)                      { this.method = v; }
    public String getStatus()                            { return status; }
    public void setStatus(String v)                      { this.status = v; }
    public Map<String, Object> getTransactionDetails()   { return transactionDetails; }
    public void setTransactionDetails(Map<String,Object> v) { this.transactionDetails = v; }
    public List<AppliedVoucherDTO> getAppliedVouchers()  { return appliedVouchers; }
    public void setAppliedVouchers(List<AppliedVoucherDTO> v) { this.appliedVouchers = v; }
    public Double getTotalDiscount()                     { return totalDiscount; }
    public void setTotalDiscount(Double v)               { this.totalDiscount = v; }
    public Double getFinalAmount()                       { return finalAmount; }
    public void setFinalAmount(Double v)                 { this.finalAmount = v; }
}
