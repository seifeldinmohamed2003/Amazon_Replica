package com.team27.amazon.user.security.auth;

import com.team27.amazon.user.model.Role;
import org.springframework.http.HttpStatus;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    public AuthResult handle(AuthContext context) {
        Role requiredRole = context.getRequiredRole();
        if (requiredRole != null && requiredRole == Role.ADMIN && context.getUser().getRole() != Role.ADMIN) {
            return AuthResult.failure(HttpStatus.FORBIDDEN.value(), "Insufficient role");
        }

        return callNext(context);
    }
}