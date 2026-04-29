package com.team27.amazon.user.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import com.team27.amazon.user.config.JwtConfigurationManager;
import com.team27.amazon.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    public String generateToken(User user) {
        JwtConfigurationManager config = JwtConfigurationManager.getInstance();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + config.getExpirationMs());

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole() != null ? user.getRole().name() : null)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

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

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Claims extractClaims(String token) {
        return parseClaims(token);
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