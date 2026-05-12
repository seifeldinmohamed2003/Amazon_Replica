package com.team27.amazon.contracts.events;

public record PaymentFailedEvent(Long transactionId, Long orderId, String reason) {}
