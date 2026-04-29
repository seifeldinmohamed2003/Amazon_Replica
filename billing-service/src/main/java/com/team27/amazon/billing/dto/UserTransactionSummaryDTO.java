package com.team27.amazon.billing.dto;

import java.util.Map;

public class UserTransactionSummaryDTO {

    private Long userId;
    private Long totalTransactions;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserTransactionSummaryDTO() {
    }

    public UserTransactionSummaryDTO(Long userId, Long totalTransactions, Double totalAmount,
                                     Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalTransactions = totalTransactions;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }

    public static class Builder {
        private Long userId;
        private Long totalTransactions;
        private Double totalAmount;
        private Map<String, Double> methodBreakdown;

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder totalTransactions(Long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder totalAmount(Double totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder methodBreakdown(Map<String, Double> methodBreakdown) {
            this.methodBreakdown = methodBreakdown;
            return this;
        }

        public UserTransactionSummaryDTO build() {
            return new UserTransactionSummaryDTO(
                    userId,
                    totalTransactions,
                    totalAmount,
                    methodBreakdown
            );
        }
    }
}