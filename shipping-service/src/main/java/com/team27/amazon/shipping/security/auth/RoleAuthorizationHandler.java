package com.team27.amazon.shipping.security.auth;

import org.springframework.http.HttpStatus;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        String requiredRole = context.getRequiredRole();
        if (requiredRole != null && !requiredRole.equalsIgnoreCase(context.getRole())) {
            return AuthResult.failure(HttpStatus.FORBIDDEN.value(), "Insufficient role");
        }

        return callNext(context);
    }
}