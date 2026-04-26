package com.team27.amazon.billing.security.handler;

public abstract class AuthHandler {

    protected AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Returns null if this handler succeeds (chain continues),
     * or an error message (e.g. "401:Missing token") if it fails.
     */
    public abstract String handle(AuthContext ctx);

    protected String passToNext(AuthContext ctx) {
        if (next != null) return next.handle(ctx);
        return null; // chain complete — success
    }
}
