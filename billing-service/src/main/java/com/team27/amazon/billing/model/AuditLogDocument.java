package com.team27.amazon.billing.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "audit_logs")
public class AuditLogDocument {
    
    @Id
    private String id;
    private String transactionId;
    private String eventType;
    private String details;
    private LocalDateTime timestamp;

    // Default Constructor (Required by Spring Data)
    public AuditLogDocument() {}

    // Full Constructor
    public AuditLogDocument(String transactionId, String eventType, String details, LocalDateTime timestamp) {
        this.transactionId = transactionId;
        this.eventType = eventType;
        this.details = details;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}