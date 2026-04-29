package com.team27.amazon.shipping.security.auth;

public abstract class AuthHandler {

    private AuthHandler next;

    public void setNext(AuthHandler next) {
        this.next = next;
    }

    protected AuthResult callNext(AuthContext context) {
        if (next == null) {
            return AuthResult.success();
        }
        return next.handle(context);
    }

    public abstract AuthResult handle(AuthContext context);
}