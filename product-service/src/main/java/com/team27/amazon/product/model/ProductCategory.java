package com.team27.amazon.product.model;

import java.util.Locale;

public enum ProductCategory {
    ELECTRONICS,
    BOOKS,
    CLOTHING,
    HOME,
    BEAUTY,
    SPORTS,
    TOYS,
    GROCERIES,
    HEALTH,
    AUTOMOTIVE,
    OTHER;

    public static ProductCategory fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);

        return ProductCategory.valueOf(normalized);
    }
}