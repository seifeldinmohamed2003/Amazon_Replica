package com.team27.amazon.contracts.events;

public record OrderItemPayload(
        Long productId,
        Integer quantity,
        Double priceAtPurchase
) {}
