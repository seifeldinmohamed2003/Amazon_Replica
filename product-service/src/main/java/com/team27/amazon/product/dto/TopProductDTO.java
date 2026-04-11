package com.team27.amazon.product.dto;

public class TopProductDTO {
    private Long productId;
    private String name;
    private Double rating;
    private Long totalSales;

    public TopProductDTO() {
    }

    public TopProductDTO(Long productId, String name, Double rating, Long totalSales) {
        this.productId = productId;
        this.name = name;
        this.rating = rating;
        this.totalSales = totalSales;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public Double getRating() {
        return rating;
    }

    public Long getTotalSales() {
        return totalSales;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public void setTotalSales(Long totalSales) {
        this.totalSales = totalSales;
    }
}