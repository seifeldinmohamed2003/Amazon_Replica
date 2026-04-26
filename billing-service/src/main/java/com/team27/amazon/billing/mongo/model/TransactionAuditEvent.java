package com.team27.amazon.billing.mongo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
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
    private Map<String, Object> details;

    public TransactionAuditEvent() {}

    @Override public String getId()               { return id; }
    public void setId(String id)                  { this.id = id; }

    public Long getTransactionId()                { return transactionId; }
    public void setTransactionId(Long v)          { this.transactionId = v; }

    @Override public String getAction()           { return action; }
    public void setAction(String v)               { this.action = v; }

    @Override public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime v)     { this.timestamp = v; }

    public String getMethod()                     { return method; }
    public void setMethod(String v)               { this.method = v; }

    public Double getAmount()                     { return amount; }
    public void setAmount(Double v)               { this.amount = v; }

    @Override public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> v)     { this.details = v; }
}
