package com.team27.amazon.shipping.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class JwtConfigurationManager {

    private static final String DEFAULT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final long DEFAULT_EXPIRATION_MS = 86_400_000L;
    private static volatile JwtConfigurationManager instance;

    private final String secret;
    private final long expirationMs;

    private JwtConfigurationManager() {
        this.secret = resolveSecret();
        this.expirationMs = resolveExpiration();
        validateSecret(this.secret);
    }

    public static JwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (JwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new JwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private String resolveSecret() {
        String secretValue = firstNonBlank(
                System.getenv("JWT_SECRET"),
                System.getProperty("JWT_SECRET"),
                DEFAULT_SECRET
        );
        return secretValue.trim();
    }

    private long resolveExpiration() {
        String expirationValue = firstNonBlank(
                System.getenv("JWT_EXPIRATION_MS"),
                System.getProperty("JWT_EXPIRATION_MS")
        );

        if (expirationValue == null) {
            return DEFAULT_EXPIRATION_MS;
        }

        try {
            return Long.parseLong(expirationValue.trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_EXPIRATION_MS;
        }
    }

    private void validateSecret(String configuredSecret) {
        byte[] keyBytes = decodeSecret(configuredSecret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
    }

    private byte[] decodeSecret(String configuredSecret) {
        try {
            return Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}