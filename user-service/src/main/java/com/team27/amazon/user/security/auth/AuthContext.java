package com.team27.amazon.user.security.auth;

import com.team27.amazon.user.model.Role;
import com.team27.amazon.user.model.User;
import jakarta.servlet.http.HttpServletRequest;

public class AuthContext {

    private final HttpServletRequest request;
    private String token;
    private User user;
    private Role requiredRole;

    public AuthContext(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Role getRequiredRole() {
        return requiredRole;
    }

    public void setRequiredRole(Role requiredRole) {
        this.requiredRole = requiredRole;
    }
}