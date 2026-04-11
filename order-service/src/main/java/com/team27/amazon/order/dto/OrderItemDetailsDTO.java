package com.team27.amazon.order.dto;

import java.util.Map;

public class OrderItemDetailsDTO {

    private Long id;
    private Integer itemOrder;
    private Long productId;
    private Integer quantity;
    private Double priceAtPurchase;
    private Map<String, Object> metadata;

    public OrderItemDetailsDTO() {
    }

    public OrderItemDetailsDTO(Long id, Integer itemOrder, Long productId, Integer quantity,
                               Double priceAtPurchase, Map<String, Object> metadata) {
        this.id = id;
        this.itemOrder = itemOrder;
        this.productId = productId;
        this.quantity = quantity;
        this.priceAtPurchase = priceAtPurchase;
        this.metadata = metadata;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getItemOrder() {
        return itemOrder;
    }

    public void setItemOrder(Integer itemOrder) {
        this.itemOrder = itemOrder;
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

    public Double getPriceAtPurchase() {
        return priceAtPurchase;
    }

    public void setPriceAtPurchase(Double priceAtPurchase) {
        this.priceAtPurchase = priceAtPurchase;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}