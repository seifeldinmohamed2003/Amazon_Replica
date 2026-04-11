package com.team27.amazon.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;

public class AddOrderItemRequestDTO {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    private Map<String, Object> metadata = new HashMap<>();

    public AddOrderItemRequestDTO() {
    }

    public AddOrderItemRequestDTO(Long productId, Integer quantity, Map<String, Object> metadata) {
        this.productId = productId;
        this.quantity = quantity;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
}