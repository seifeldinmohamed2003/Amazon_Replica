package com.team27.amazon.contracts.events;

public record UserRegisteredEvent(Long userId, String email, String role) {}
