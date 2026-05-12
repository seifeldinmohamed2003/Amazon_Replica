package com.team27.amazon.contracts.events;

public record ShipmentStatusChangedEvent(
        Long shipmentId,
        Long orderId,
        String newStatus
) {}
