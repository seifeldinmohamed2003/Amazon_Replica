package com.team27.amazon.billing.security.handler;

import com.team27.amazon.billing.security.jwt.JwtService;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public String handle(AuthContext ctx) {
        try {
            if (!jwtService.validateToken(ctx.getToken())) {
                return "401:Invalid or expired token";
            }
            ctx.setEmail(jwtService.extractEmail(ctx.getToken()));
            ctx.setUserId(jwtService.extractUserId(ctx.getToken()));
            ctx.setRole(jwtService.extractRole(ctx.getToken()));
        } catch (Exception e) {
            return "401:Invalid or expired token";
        }
        return passToNext(ctx);
    }
}
