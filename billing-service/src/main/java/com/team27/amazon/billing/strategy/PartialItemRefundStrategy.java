package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;

import java.util.List;
import java.util.Map;

public class PartialItemRefundStrategy implements RefundStrategy {

    /**
     * Map of orderItemId → (priceAtPurchase * quantity) for every item
     * in the transaction's order. Passed in by RefundStrategySelector.
     */
    private final Map<Long, Double> itemAmounts;

    public PartialItemRefundStrategy(Map<Long, Double> itemAmounts) {
        this.itemAmounts = itemAmounts;
    }

    @Override
    public RefundResult calculateRefund(Transaction transaction, RefundRequest request) {
        List<Long> requestedIds = request.getOrderItemIds();

        double total = requestedIds.stream()
                .mapToDouble(id -> itemAmounts.getOrDefault(id, 0.0))
                .sum();

        return new RefundResult(
                total,
                "PARTIAL_REFUND",
                requestedIds,
                "PartialItemRefundStrategy"
        );
    }
}
