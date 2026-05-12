package com.team27.amazon.contracts.events;

public record ProductRatedEvent(Long productId, Double newAverage, Integer totalRatings) {}
