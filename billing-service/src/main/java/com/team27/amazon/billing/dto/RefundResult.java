package com.team27.amazon.billing.dto;

import java.util.List;

public class RefundResult {
    private double refundAmount;
    private String reasonCode;
    private List<Long> refundedItemIds;
    private String strategyName;

    public RefundResult(double refundAmount, String reasonCode,
                        List<Long> refundedItemIds, String strategyName) {
        this.refundAmount    = refundAmount;
        this.reasonCode      = reasonCode;
        this.refundedItemIds = refundedItemIds;
        this.strategyName    = strategyName;
    }

    public double getRefundAmount()       { return refundAmount; }
    public String getReasonCode()         { return reasonCode; }
    public List<Long> getRefundedItemIds(){ return refundedItemIds; }
    public String getStrategyName()       { return strategyName; }
}
