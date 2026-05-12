package com.team27.amazon.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record OrderDTO(
        Long id,
        Long userId,
        Long shippingAddressId,
        String status,
        Double totalAmount,
        LocalDateTime orderedAt,
        LocalDateTime deliveredAt,
        Map<String, Object> metadata
) {}
