package com.team27.amazon.billing.strategy;

import com.team27.amazon.billing.dto.RefundRequest;
import com.team27.amazon.billing.dto.RefundResult;
import com.team27.amazon.billing.model.Transaction;

public interface RefundStrategy {
    RefundResult calculateRefund(Transaction transaction, RefundRequest request);
}