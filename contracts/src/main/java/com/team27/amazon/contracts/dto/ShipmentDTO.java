package com.team27.amazon.contracts.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ShipmentDTO(
        Long id,
        Long orderId,
        String carrier,
        String trackingNumber,
        String status,
        LocalDate estimatedDelivery,
        LocalDate actualDelivery,
        LocalDateTime lastUpdate,
        Double latitude,
        Double longitude,
        Map<String, Object> metadata
) {}
