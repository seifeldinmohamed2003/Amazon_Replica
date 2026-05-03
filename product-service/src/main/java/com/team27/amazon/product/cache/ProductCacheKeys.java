package com.team27.amazon.product.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.StringJoiner;

public final class ProductCacheKeys {

    private static final String SERVICE = "product-service";

    private ProductCacheKeys() {}

    public static String productDetail(Long id) {
        return SERVICE + "::product::" + id;
    }

    public static String productReviewDetail(Long id) {
        return SERVICE + "::product-review::" + id;
    }

    public static String s2f1Search(String category, Double minPrice, Double maxPrice) {
        return feature("S2-F1", category, minPrice, maxPrice);
    }

    public static String s2f3Sales(Long productId, LocalDate startDate, LocalDate endDate) {
        return feature("S2-F3", productId, startDate, endDate);
    }

    public static String s2f5Specifications(String key, String value, String status) {
        return feature("S2-F5", key, value, status);
    }

    public static String s2f6TopRated(Integer limit) {
        return feature("S2-F6", limit);
    }

    public static String s2f9LowStock(Integer threshold) {
        return feature("S2-F9", threshold);
    }

    public static String s2f10FullText(
            String query,
            String category,
            String brand,
            String status,
            Double minPrice,
            Double maxPrice,
            Double minRating,
            Double maxRating
    ) {
        return feature("S2-F10", query, category, brand, status, minPrice, maxPrice, minRating, maxRating);
    }
     
    public static String s2f12CatalogDashboard() {
        return feature("S2-F12", "catalog-dashboard");
    }

    public static String featurePattern(String featureId) {
        return SERVICE + "::" + featureId + "::*";
    }

    private static String feature(String featureId, Object... params) {
        StringJoiner joiner = new StringJoiner("|");

        for (Object param : params) {
            joiner.add(param == null ? "null" : String.valueOf(param));
        }

        return SERVICE + "::" + featureId + "::" + sha256(joiner.toString());
    }

    private static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(encoded);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash cache key", ex);
        }
    }
}