package com.team27.amazon.order.security.auth;

import com.team27.amazon.order.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

public class UserLoaderHandler extends AuthHandler {

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        Long userId = jwtService.extractUserId(context.getToken());
        if (userId == null) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "Token is missing the uid claim");
        }

        Integer matches = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM users WHERE id = ?", Integer.class, userId);
        if (matches == null || matches <= 0) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "User not found");
        }

        context.setUserId(userId);
        context.setRole(jwtService.extractRole(context.getToken()));
        return callNext(context);
    }
}