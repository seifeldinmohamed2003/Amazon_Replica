package com.team27.amazon.user.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {

    @Id
    private String id;

    private Long userId;
    private String email;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public AuthEvent() {}

    public AuthEvent(Long userId, String email, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.userId = userId;
        this.email = email;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    @Override
    public String getId() { return id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getAction() { return action; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    // getters & setters
}