package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RefundStrategySelector {

    private final TransactionRepository transactionRepository;

    public RefundStrategySelector(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public RefundStrategy select(Transaction transaction, RefundRequest request) {
        boolean withinWindow = transaction.getCreatedAt()
                .isAfter(LocalDateTime.now().minusDays(30));

        if (!withinWindow) {
            return new NoRefundStrategy();
        }

        if (request.isRefundAll()) {
            return new FullItemRefundStrategy(transactionRepository);
        }

        return new PartialItemRefundStrategy(transactionRepository);
    }
}