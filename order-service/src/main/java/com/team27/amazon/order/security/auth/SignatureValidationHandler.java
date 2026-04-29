package com.team27.amazon.order.security.auth;

import com.team27.amazon.order.security.JwtService;
import org.springframework.http.HttpStatus;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public AuthResult handle(AuthContext context) {
        if (!jwtService.validateToken(context.getToken())) {
            return AuthResult.failure(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
        }

        return callNext(context);
    }
}