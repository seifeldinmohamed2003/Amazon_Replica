package com.team27.amazon.order.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import com.team27.amazon.order.config.JwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public Long extractUserId(String token) {
        Object uid = parseClaims(token).get("uid");
        if (uid instanceof Number number) {
            return number.longValue();
        }
        if (uid instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        return null;
    }

    public String extractRole(String token) {
        Object role = parseClaims(token).get("role");
        return role != null ? role.toString() : null;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = decodeSecret(JwtConfigurationManager.getInstance().getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] decodeSecret(String configuredSecret) {
        try {
            return java.util.Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            return configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
    }
}