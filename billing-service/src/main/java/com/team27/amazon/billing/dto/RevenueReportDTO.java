package com.team27.amazon.billing.dto;

public class RevenueReportDTO {

    private Double totalRevenue;
    private Long totalTransactions;
    private Double averageTransaction;
    private Double refundedAmount;
    private Long refundCount;

    public RevenueReportDTO() {}

    public RevenueReportDTO(Double totalRevenue, Long totalTransactions, Double averageTransaction,
                            Double refundedAmount, Long refundCount) {
        this.totalRevenue = totalRevenue;
        this.totalTransactions = totalTransactions;
        this.averageTransaction = averageTransaction;
        this.refundedAmount = refundedAmount;
        this.refundCount = refundCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Double v) { this.totalRevenue = v; }

    public Long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Long v) { this.totalTransactions = v; }

    public Double getAverageTransaction() { return averageTransaction; }
    public void setAverageTransaction(Double v) { this.averageTransaction = v; }

    public Double getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(Double v) { this.refundedAmount = v; }

    public Long getRefundCount() { return refundCount; }
    public void setRefundCount(Long v) { this.refundCount = v; }

    public static class Builder {
        private Double totalRevenue;
        private Long totalTransactions;
        private Double averageTransaction;
        private Double refundedAmount;
        private Long refundCount;

        public Builder totalRevenue(Double totalRevenue) {
            this.totalRevenue = totalRevenue;
            return this;
        }

        public Builder totalTransactions(Long totalTransactions) {
            this.totalTransactions = totalTransactions;
            return this;
        }

        public Builder averageTransaction(Double averageTransaction) {
            this.averageTransaction = averageTransaction;
            return this;
        }

        public Builder refundedAmount(Double refundedAmount) {
            this.refundedAmount = refundedAmount;
            return this;
        }

        public Builder refundCount(Long refundCount) {
            this.refundCount = refundCount;
            return this;
        }

        public RevenueReportDTO build() {
            return new RevenueReportDTO(
                    totalRevenue,
                    totalTransactions,
                    averageTransaction,
                    refundedAmount,
                    refundCount
            );
        }
    }
}