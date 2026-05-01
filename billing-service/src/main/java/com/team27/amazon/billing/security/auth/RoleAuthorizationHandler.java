package com.team27.amazon.billing.security.auth;

import org.springframework.http.HttpStatus;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        String requiredRole = context.getRequiredRole();
        String actualRole = context.getRole();

        if (requiredRole == null || requiredRole.isBlank()) {
            return callNext(context);
        }

        if (actualRole == null || actualRole.isBlank()) {
            return AuthResult.failure(HttpStatus.FORBIDDEN.value(), "Insufficient role");
        }

        // ADMIN can access CUSTOMER-level endpoints too
        if ("ADMIN".equalsIgnoreCase(actualRole)) {
            return callNext(context);
        }

        if (!requiredRole.equalsIgnoreCase(actualRole)) {
            return AuthResult.failure(HttpStatus.FORBIDDEN.value(), "Insufficient role");
        }

        return callNext(context);
    }
}