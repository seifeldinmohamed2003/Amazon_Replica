package com.team27.amazon.billing.security.handler;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    public String handle(AuthContext ctx) {
        String authHeader = ctx.getRequest().getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "401:Missing or malformed Authorization header";
        }
        ctx.setToken(authHeader.substring(7));
        return passToNext(ctx);
    }
}
