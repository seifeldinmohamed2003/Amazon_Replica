package com.team27.amazon.order.dto;

public class OrderEstimateDTO {

    private Integer itemCount;
    private Double subtotal;
    private Double shippingCost;
    private Double estimatedTotal;
    private Double discountApplied;

    public OrderEstimateDTO() {
    }

    public OrderEstimateDTO(
            Integer itemCount,
            Double subtotal,
            Double shippingCost,
            Double estimatedTotal,
            Double discountApplied) {
        this.itemCount = itemCount;
        this.subtotal = subtotal;
        this.shippingCost = shippingCost;
        this.estimatedTotal = estimatedTotal;
        this.discountApplied = discountApplied;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = subtotal;
    }

    public Double getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(Double shippingCost) {
        this.shippingCost = shippingCost;
    }

    public Double getEstimatedTotal() {
        return estimatedTotal;
    }

    public void setEstimatedTotal(Double estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public void setDiscountApplied(Double discountApplied) {
        this.discountApplied = discountApplied;
    }
}
