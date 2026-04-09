package com.team27.amazon.user.dto;


public class TopBuyerDTO {
    private Long userId;
    private String name;
    private Double totalSpent;
    private Long orderCount;

    public TopBuyerDTO() {
    }

    public TopBuyerDTO(Long userId, String name, Double totalSpent, Long orderCount) {
        this.userId = userId;
        this.name = name;
        this.totalSpent = totalSpent;
        this.orderCount = orderCount;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(Double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }
}