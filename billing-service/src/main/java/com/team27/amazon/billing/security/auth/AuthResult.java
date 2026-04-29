package com.team27.amazon.billing.security.auth;

public final class AuthResult {

    private final boolean success;
    private final int status;
    private final String message;

    private AuthResult(boolean success, int status, String message) {
        this.success = success;
        this.status = status;
        this.message = message;
    }

    public static AuthResult success() {
        return new AuthResult(true, 200, null);
    }

    public static AuthResult failure(int status, String message) {
        return new AuthResult(false, status, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}