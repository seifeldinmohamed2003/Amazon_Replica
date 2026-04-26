package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;

import java.util.List;

public class FullItemRefundStrategy implements RefundStrategy {

    private final List<Long> allOrderItemIds;

    public FullItemRefundStrategy(List<Long> allOrderItemIds) {
        this.allOrderItemIds = allOrderItemIds;
    }

    @Override
    public RefundResult calculateRefund(Transaction transaction, RefundRequest request) {
        return new RefundResult(
                transaction.getAmount(),
                "FULL_REFUND",
                allOrderItemIds,
                "FullItemRefundStrategy"
        );
    }
}
