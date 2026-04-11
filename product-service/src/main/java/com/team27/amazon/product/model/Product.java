package com.team27.amazon.product.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ProductCategory category = ProductCategory.OTHER;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private Integer stockQuantity = 0;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(nullable = false)
    private Integer totalRatings = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> specifications = new HashMap<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductReview> productReviews = new ArrayList<>();

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (stockQuantity == null) {
            stockQuantity = 0;
        }
        if (rating == null) {
            rating = 0.0;
        }
        if (totalRatings == null) {
            totalRatings = 0;
        }
        if (status == null) {
            status = ProductStatus.ACTIVE;
        }
        if (specifications == null) {
            specifications = new HashMap<>();
        }
    }

    public void addReview(ProductReview review) {
        if (review == null) {
            return;
        }
        productReviews.add(review);
        review.setProduct(this);
    }

    public void removeReview(ProductReview review) {
        if (review == null) {
            return;
        }
        productReviews.remove(review);
        review.setProduct(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getCategory() {
        return category == null ? null : category.name();
    }

    public void setCategory(String category) {
        if (category == null || category.isBlank()) {
            this.category = ProductCategory.OTHER;
            return;
        }
        this.category = ProductCategory.valueOf(category.trim().toUpperCase());
    }

    public ProductCategory getCategoryEnum() {
        return category;
    }

    public void setCategoryEnum(ProductCategory category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public Map<String, Object> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(Map<String, Object> specifications) {
        this.specifications = specifications;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<ProductReview> getProductReviews() {
        return productReviews;
    }

    public void setProductReviews(List<ProductReview> productReviews) {
        this.productReviews.clear();
        if (productReviews == null) {
            return;
        }
        for (ProductReview review : productReviews) {
            addReview(review);
        }
    }
}
