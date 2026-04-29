package com.team27.amazon.billing.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class LifecycleEventDTO {
    private LocalDateTime timestamp;
    private String source;
    private String action;
    private Map<String, Object> details;

    public LifecycleEventDTO() {}

    public LifecycleEventDTO(LocalDateTime timestamp, String source, String action, Map<String, Object> details) {
        this.timestamp = timestamp;
        this.source = source;
        this.action = action;
        this.details = details;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}