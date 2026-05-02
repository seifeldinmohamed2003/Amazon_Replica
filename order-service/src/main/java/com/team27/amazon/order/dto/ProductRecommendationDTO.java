package com.team27.amazon.order.dto;

import java.math.BigDecimal;

public record ProductRecommendationDTO(
        Long productId,
        String name,
        String category,
        String brand,
        BigDecimal price,
        Long score
) {}