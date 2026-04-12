package com.team27.amazon.order.dto;

import java.util.List;
import java.util.Map;

public class OrderDetailsDTO {

    private Long orderId;
    private Long userId;
    private Long shippingAddressId;
    private String status;
    private Double totalAmount;
    private Map<String, Object> metadata;
    private List<OrderItemDetailsDTO> items;
    private Integer totalItems;
    private Integer totalQuantity;

    public OrderDetailsDTO() {
    }

    public OrderDetailsDTO(Long orderId, Long userId, Long shippingAddressId, String status,
                           Double totalAmount, Map<String, Object> metadata,
                           List<OrderItemDetailsDTO> items, Integer totalItems, Integer totalQuantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.shippingAddressId = shippingAddressId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.metadata = metadata;
        this.items = items;
        this.totalItems = totalItems;
        this.totalQuantity = totalQuantity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getShippingAddressId() {
        return shippingAddressId;
    }

    public void setShippingAddressId(Long shippingAddressId) {
        this.shippingAddressId = shippingAddressId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public List<OrderItemDetailsDTO> getItems() {
        return items;
    }

    public void setItems(List<OrderItemDetailsDTO> items) {
        this.items = items;
    }

    public Integer getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(Integer totalItems) {
        this.totalItems = totalItems;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(Integer totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}