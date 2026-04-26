package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;

import java.util.List;

public class NoRefundStrategy implements RefundStrategy {

    @Override
    public RefundResult calculateRefund(Transaction transaction, RefundRequest request) {
        return new RefundResult(
                0.0,
                "return window expired",
                List.of(),
                "NoRefundStrategy"
        );
    }
}
