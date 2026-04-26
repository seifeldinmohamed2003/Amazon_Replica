package com.team27.amazon.billing.security.handler;

public class RoleAuthorizationHandler extends AuthHandler {

    private final String requiredRole;

    public RoleAuthorizationHandler(String requiredRole) {
        this.requiredRole = requiredRole;
    }

    @Override
    public String handle(AuthContext ctx) {
        if (requiredRole != null && !requiredRole.equals(ctx.getRole())) {
            return "403:Insufficient role";
        }
        return passToNext(ctx);
    }
}
