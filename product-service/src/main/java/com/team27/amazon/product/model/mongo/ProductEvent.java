package com.team27.amazon.product.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "product_events")
public class ProductEvent implements MongoEvent {

    @Id
    private String id;

    private Long productId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public ProductEvent() {}

    public ProductEvent(Long productId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.productId = productId;
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
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public void setId(String id) { this.id = id; }

    public void setAction(String action) { this.action = action; }

    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public void setDetails(Map<String, Object> details) { this.details = details; }
}