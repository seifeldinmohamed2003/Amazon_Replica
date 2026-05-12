package com.team27.amazon.contracts.events;

public record PaymentCompletedEvent(Long transactionId, Long orderId, Long userId, Double amount) {}
