package com.team27.amazon.contracts.events;

public record PaymentRefundedEvent(Long transactionId, Long orderId, Double refundAmount) {}
