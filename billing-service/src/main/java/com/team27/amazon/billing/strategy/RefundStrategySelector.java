package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.model.Transaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class RefundStrategySelector {

    private static final int RETURN_WINDOW_DAYS = 30;

    private final List<Long> allOrderItemIds;
    private final Map<Long, Double> itemAmounts;

    public RefundStrategySelector(List<Long> allOrderItemIds,
                                  Map<Long, Double> itemAmounts) {
        this.allOrderItemIds = allOrderItemIds;
        this.itemAmounts     = itemAmounts;
    }

    public RefundStrategy select(Transaction transaction, RefundRequest request) {
        boolean withinWindow = transaction.getCreatedAt()
                .isAfter(LocalDateTime.now().minusDays(RETURN_WINDOW_DAYS));

        if (!withinWindow) {
            return new NoRefundStrategy();
        }
        if (Boolean.TRUE.equals(request.getRefundAll())) {
            return new FullItemRefundStrategy(allOrderItemIds);
        }
        return new PartialItemRefundStrategy(itemAmounts);
    }
}
