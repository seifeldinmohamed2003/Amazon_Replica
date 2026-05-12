package com.team27.amazon.contracts.events;

public record ShipmentCreatedEvent(
        Long shipmentId,
        Long orderId,
        String carrier,
        String trackingNumber
) {}
