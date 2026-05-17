package com.team27.amazon.gateway.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GatewayJwtConfigurationManager {

    private static final String DEFAULT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static volatile GatewayJwtConfigurationManager instance;

    private final String secret;

    private GatewayJwtConfigurationManager() {
        this.secret = resolveSecret();
    }

    public static GatewayJwtConfigurationManager getInstance() {
        if (instance == null) {
            synchronized (GatewayJwtConfigurationManager.class) {
                if (instance == null) {
                    instance = new GatewayJwtConfigurationManager();
                }
            }
        }
        return instance;
    }

    public String getSecret() {
        return secret;
    }

    public byte[] getKeyBytes() {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String resolveSecret() {
        String fromEnv = System.getenv("JWT_SECRET");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        String fromProp = System.getProperty("JWT_SECRET");
        if (fromProp != null && !fromProp.isBlank()) return fromProp.trim();
        return DEFAULT_SECRET;
    }
}
