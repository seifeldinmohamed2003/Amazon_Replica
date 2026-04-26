package com.team27.amazon.billing.security.jwt;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfigBridge {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMs;

    @PostConstruct
    public void init() {
        JwtConfigurationManager.initConfig(secret, expirationMs);
    }
}
