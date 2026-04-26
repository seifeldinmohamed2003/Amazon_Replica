package com.team27.amazon.billing.security.jwt;

/**
 * GoF Singleton — holds shared immutable JWT configuration.
 * NOT a Spring bean (@Component is intentionally absent).
 * JwtService obtains config via JwtConfigurationManager.getInstance().
 */
public class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private String secret;
    private long expirationMs;

    // ── private constructor ────────────────────────────────────────────────
    private JwtConfigurationManager() {
        // Primary: read from environment (set by Docker Compose)
        String envSecret = System.getenv("JWT_SECRET");
        String envExp    = System.getenv("JWT_EXPIRATION_MS");

        this.secret      = (envSecret != null && !envSecret.isBlank())
                ? envSecret
                : "bXlTdXBlclNlY3JldEtleUZvckpXVEF1dGhlbnRpY2F0aW9uMjAyNg==";
        this.expirationMs = (envExp != null && !envExp.isBlank())
                ? Long.parseLong(envExp)
                : 86_400_000L; // 24 h
    }

    // ── double-checked locking (thread-safe) ───────────────────────────────
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

    // ── singleton-bridge: Spring bean pushes values from application.yml ───
    public static void initConfig(String secret, long expirationMs) {
        JwtConfigurationManager mgr = getInstance();
        if (secret != null && !secret.isBlank()) {
            mgr.secret = secret;
        }
        if (expirationMs > 0) {
            mgr.expirationMs = expirationMs;
        }
    }

    public String getSecret()       { return secret; }
    public long   getExpirationMs() { return expirationMs; }
}
