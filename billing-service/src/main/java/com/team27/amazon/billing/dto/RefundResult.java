package com.team27.amazon.billing.dto;

import java.util.List;

public class RefundResult {

    private Double amount;
    private String reasonCode;
    private List<Long> refundedItemIds;

    public RefundResult(Double amount, String reasonCode, List<Long> refundedItemIds) {
        this.amount = amount;
        this.reasonCode = reasonCode;
        this.refundedItemIds = refundedItemIds;
    }

    public Double getAmount() { return amount; }
    public String getReasonCode() { return reasonCode; }
    public List<Long> getRefundedItemIds() { return refundedItemIds; }
}