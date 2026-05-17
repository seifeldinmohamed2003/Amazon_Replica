package com.team27.amazon.gateway.filter;

import com.team27.amazon.gateway.config.GatewayJwtConfigurationManager;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.UUID;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_BYPASS_PATH = "/api/auth/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Generate or forward X-Correlation-ID
        String corrId = request.getHeaders().getFirst("X-Correlation-ID");
        if (corrId == null || corrId.isBlank()) {
            corrId = UUID.randomUUID().toString();
        }
        final String correlationId = corrId;

        // Bypass JWT check for /api/auth/**
        if (path.startsWith(AUTH_BYPASS_PATH)) {
            ServerHttpRequest mutated = request.mutate()
                    .header("X-Correlation-ID", correlationId)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        // Extract token
        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Validate token and extract claims
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Extract uid and role
        String userId = String.valueOf(claims.get("uid"));
        String role = claims.get("role") != null ? claims.get("role").toString() : "";

        // Forward headers downstream
        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .header("X-Correlation-ID", correlationId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters
    }

    private SecretKey signingKey() {
        byte[] keyBytes = GatewayJwtConfigurationManager.getInstance().getKeyBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}