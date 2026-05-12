package com.team27.amazon.contracts.events;

import java.util.List;

public record OrderCancelledEvent(
        Long orderId,
        Long userId,
        List<OrderItemPayload> restoredItems,
        String reason
) {}
