package com.team27.amazon.contracts.dto;

import java.util.Map;

public record ProductDTO(
        Long id,
        String name,
        String description,
        Double price,
        String category,
        String brand,
        Integer stockQuantity,
        String status,
        Double rating,
        Map<String, Object> specifications
) {}
