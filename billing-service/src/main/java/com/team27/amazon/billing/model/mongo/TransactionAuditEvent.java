package com.team27.amazon.billing.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "transaction_audit_trail")
public class TransactionAuditEvent implements MongoEvent {

    @Id
    private String id;

    private Long transactionId;
    private String action;
    private LocalDateTime timestamp;
    private String method;
    private Double amount;
    private Map<String, Object> details = new HashMap<>();


    public TransactionAuditEvent() {}

    public TransactionAuditEvent(Long transactionId, String action, LocalDateTime timestamp,String method,Double amount, Map<String, Object> details) {
        this.transactionId = transactionId;
        this.action = action;
        this.timestamp = timestamp;
        this.method = method;
        this.amount = amount;
        this.details = details != null ? details : new HashMap<>();
    }

    public static TransactionAuditEvent paymentEvent(Long transactionId,
                                                     String action,
                                                     LocalDateTime timestamp,
                                                     String method,
                                                     Double amount,
                                                     Map<String, Object> details) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method is required for payment-shaped transaction audit events");
        }

        if (amount == null) {
            throw new IllegalArgumentException("amount is required for payment-shaped transaction audit events");
        }

        return new TransactionAuditEvent(transactionId, action, timestamp, method, amount, details);
    }
    public static TransactionAuditEvent analyticsViewed(LocalDateTime timestamp, Map<String, Object> details) {
        return new TransactionAuditEvent(null, "ANALYTICS_VIEWED", timestamp, null, null, details);
    }

    // getters & setters
    @Override
    public String getId() { return id; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String getAction() { return action; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    public String getMethod() {
        return method;
    }

    public Double getAmount() {
        return amount;
    }
    public Long getTransactionId() { return transactionId; }

    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public void setId(String id) { this.id = id; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setMethod(String method) {
        this.method = method;
    }
    public void setAmount(Double amount) {
        this.amount = amount;
    }
    public void setDetails(Map<String, Object> details) { this.details = details != null ? details : new HashMap<>(); }
}