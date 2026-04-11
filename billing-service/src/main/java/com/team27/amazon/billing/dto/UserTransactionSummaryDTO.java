package com.team27.amazon.billing.dto;

import java.util.Map;

public class UserTransactionSummaryDTO {
    private Long userId;
    private Long totalTransactions;
    private Double totalAmount;
    private Map<String, Double> methodBreakdown;

    public UserTransactionSummaryDTO(Long userId, Long totalTransactions, Double totalAmount, Map<String, Double> methodBreakdown) {
        this.userId = userId;
        this.totalTransactions = totalTransactions;
        this.totalAmount = totalAmount;
        this.methodBreakdown = methodBreakdown;
    }

    // Getters & Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Long totalTransactions) { this.totalTransactions = totalTransactions; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, Double> getMethodBreakdown() { return methodBreakdown; }
    public void setMethodBreakdown(Map<String, Double> methodBreakdown) { this.methodBreakdown = methodBreakdown; }
}