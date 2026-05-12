package com.team27.amazon.contracts.events;

public record ProductDiscontinuedEvent(Long productId, String oldStatus, String newStatus) {}
