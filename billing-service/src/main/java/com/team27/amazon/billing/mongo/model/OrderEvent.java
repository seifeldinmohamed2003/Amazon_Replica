package com.team27.amazon.billing.mongo.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/** Used by S5-F11 to read order_events from MongoDB */
@Document(collection = "order_events")
public class OrderEvent implements MongoEvent {
    @Id private String id;
    private Long orderId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public OrderEvent() {}

    @Override public String getId()                   { return id; }
    public void setId(String id)                      { this.id = id; }
    public Long getOrderId()                          { return orderId; }
    public void setOrderId(Long v)                    { this.orderId = v; }
    @Override public String getAction()               { return action; }
    public void setAction(String v)                   { this.action = v; }
    @Override public LocalDateTime getTimestamp()     { return timestamp; }
    public void setTimestamp(LocalDateTime v)         { this.timestamp = v; }
    @Override public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> v)     { this.details = v; }
}
