package com.team27.amazon.billing.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private Long id;

    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Double price;

    private String category;

    private String brand;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    private Double rating;

    @Column(name = "total_ratings")
    private Integer totalRatings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> specifications = new HashMap<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public String getBrand() {
        return brand;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public Map<String, Object> getSpecifications() {
        return specifications;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}