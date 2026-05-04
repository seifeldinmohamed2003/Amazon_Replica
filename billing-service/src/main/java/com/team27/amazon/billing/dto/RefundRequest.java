package com.team27.amazon.billing.dto;

import java.util.List;

public class RefundRequest {

    private String reason;
    private boolean refundAll;
    private List<Long> orderItemIds;

    public RefundRequest() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isRefundAll() { return refundAll; }
    public void setRefundAll(boolean refundAll) { this.refundAll = refundAll; }

    public List<Long> getOrderItemIds() { return orderItemIds; }
    public void setOrderItemIds(List<Long> orderItemIds) { this.orderItemIds = orderItemIds; }
}