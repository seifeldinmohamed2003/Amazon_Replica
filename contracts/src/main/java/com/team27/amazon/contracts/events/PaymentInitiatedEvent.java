package com.team27.amazon.contracts.events;

public record PaymentInitiatedEvent(Long transactionId, Long orderId, Long userId, Double amount) {}
