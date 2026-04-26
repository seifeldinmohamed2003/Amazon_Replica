package com.team27.amazon.billing.dto;

import java.util.Map;

public class UserTransactionSummaryDTO {

    private Long userId;
    private Long totalTransactions;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserTransactionSummaryDTO() {}

    public UserTransactionSummaryDTO(Long userId, Long totalTransactions,
                                     Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId            = userId;
        this.totalTransactions = totalTransactions;
        this.totalAmount       = totalAmount;
        this.methodBreakdown   = methodBreakdown;
    }

    // ── Builder ────────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final UserTransactionSummaryDTO dto = new UserTransactionSummaryDTO();
        public Builder userId(Long v)                      { dto.userId = v;             return this; }
        public Builder totalTransactions(Long v)           { dto.totalTransactions = v;  return this; }
        public Builder totalAmount(Double v)               { dto.totalAmount = v;        return this; }
        public Builder methodBreakdown(Map<String,Double> v){ dto.methodBreakdown = v;  return this; }
        public UserTransactionSummaryDTO build()           { return dto; }
    }

    public Long                 getUserId()            { return userId; }
    public void                 setUserId(Long v)      { this.userId = v; }
    public Long                 getTotalTransactions() { return totalTransactions; }
    public void                 setTotalTransactions(Long v) { this.totalTransactions = v; }
    public Double               getTotalAmount()       { return totalAmount; }
    public void                 setTotalAmount(Double v){ this.totalAmount = v; }
    public Map<String, Double>  getMethodBreakdown()   { return methodBreakdown; }
    public void                 setMethodBreakdown(Map<String, Double> v) { this.methodBreakdown = v; }
}
