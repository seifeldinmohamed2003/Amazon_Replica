package com.team27.amazon.contracts.dto;

import java.util.Map;

public record OrderItemDTO(
        Long id,
        Long orderId,
        Long productId,
        Integer quantity,
        Double priceAtPurchase,
        Integer itemOrder,
        Map<String, Object> metadata
) {}
