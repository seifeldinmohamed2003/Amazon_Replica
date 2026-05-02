package com.team27.amazon.user.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    public static String entityKey(String entity, Long id) {
        return CacheConstants.SERVICE + "::" + entity + "::" + id;
    }

    public static String featureKey(String featureId, Object rawParams) {
        return CacheConstants.SERVICE + "::" + featureId + "::" + sha256(String.valueOf(rawParams));
    }

    public static String featureKeyFromParams(String featureId, Map<String, ?> params) {
        TreeMap<String, ?> sorted = new TreeMap<>(params);

        String canonical = sorted.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + normalize(e.getValue()))
                .collect(Collectors.joining("&"));

        return CacheConstants.SERVICE + "::" + featureId + "::" + sha256(canonical);
    }

    public static String featureKeyDirect(String featureId, String directValue) {
        return CacheConstants.SERVICE + "::" + featureId + "::" + directValue;
    }

    private static String normalize(Object value) {
        return value == null ? "null" : String.valueOf(value).trim();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : encoded) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash cache key", e);
        }
    }
}