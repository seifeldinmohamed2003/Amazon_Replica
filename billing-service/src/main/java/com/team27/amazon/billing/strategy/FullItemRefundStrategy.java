package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.repository.TransactionRepository;

import java.util.List;

public class FullItemRefundStrategy implements RefundStrategy {

    private final TransactionRepository transactionRepository;

    public FullItemRefundStrategy(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RefundResult calculateRefund(Transaction transaction, RefundRequest request) {
        // Get all order item IDs belonging to this transaction's order
        List<Long> allItemIds = transactionRepository
                .findOrderItemIdsByOrderId(transaction.getOrderId());

        return new RefundResult(
                transaction.getAmount(),
                "FULL_REFUND",
                allItemIds
        );
    }
}