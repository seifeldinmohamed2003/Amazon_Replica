package com.team27.amazon.contracts.events;

public record ProductReviewAddedEvent(Long productId, Long reviewId, Long userId, Integer rating) {}
