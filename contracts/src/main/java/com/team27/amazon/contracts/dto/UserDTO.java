package com.team27.amazon.contracts.dto;

import java.util.Map;

public record UserDTO(
        Long id,
        String name,
        String email,
        String phone,
        String role,
        String status,
        Map<String, Object> preferences
) {}
