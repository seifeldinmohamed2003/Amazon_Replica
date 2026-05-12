package com.team27.amazon.contracts.events;

public record OrderCompletedEvent(
        Long orderId,
        Long userId,
        Long shippingAddressId,
        Double totalAmount
) {}
