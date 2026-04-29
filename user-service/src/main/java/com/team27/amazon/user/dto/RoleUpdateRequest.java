package com.team27.amazon.user.dto;

import com.team27.amazon.user.model.Role;

public class RoleUpdateRequest {

    private Role role;

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}