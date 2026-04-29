package com.team27.amazon.product.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        String header = context.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid Authorization header");
        }

        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "Missing token");
        }

        context.setToken(token);
        return callNext(context);
    }
}