package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.repository.TransactionRepository;

import java.util.List;

public class PartialItemRefundStrategy implements RefundStrategy {

    private final TransactionRepository transactionRepository;

    public PartialItemRefundStrategy(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RefundResult calculateRefund(Transaction transaction, RefundRequest request) {
        List<Long> itemIds = request.getOrderItemIds();

        // Sum priceAtPurchase * quantity for only the requested items
        Double partialAmount = transactionRepository.sumItemAmounts(itemIds);
        if (partialAmount == null) partialAmount = 0.0;

        return new RefundResult(
                partialAmount,
                "PARTIAL_REFUND",
                itemIds
        );
    }
}