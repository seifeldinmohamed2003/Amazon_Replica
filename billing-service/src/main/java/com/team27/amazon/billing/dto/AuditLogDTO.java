package com.team27.amazon.billing.dto;

import java.time.LocalDateTime;

public class AuditLogDTO {
    private String transactionId;
    private String eventType;
    private String details;
    private LocalDateTime timestamp;

    // 1. Private constructor so it can only be created via the Builder
    private AuditLogDTO(Builder builder) {
        this.transactionId = builder.transactionId;
        this.eventType = builder.eventType;
        this.details = builder.details;
        this.timestamp = builder.timestamp;
    }

    // 2. Standard Getters (Required for JSON serialization)
    public String getTransactionId() { return transactionId; }
    public String getEventType() { return eventType; }
    public String getDetails() { return details; }
    public LocalDateTime getTimestamp() { return timestamp; }

    // 3. Static method to initialize the builder
    public static Builder builder() {
        return new Builder();
    }

    // 4. The actual Builder Inner Class
    public static class Builder {
        private String transactionId;
        private String eventType;
        private String details;
        private LocalDateTime timestamp;

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public AuditLogDTO build() {
            return new AuditLogDTO(this);
        }
    }
}