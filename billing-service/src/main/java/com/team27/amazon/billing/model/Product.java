package com.team27.amazon.billing.model;

import jakarta.persistence.*; 

@Entity
@Table(name = "products")
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String category;
    private String name;

    public Product() {}

    private Product(Builder builder) {
        this.id = builder.id;
        this.category = builder.category;
        this.name = builder.name;
    }

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getName() { return name; }

    public void setId(Long id) { this.id = id; }
    public void setCategory(String category) { this.category = category; }
    public void setName(String name) { this.name = name; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String category;
        private String name;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Product build() {
            return new Product(this);
        }
    }
}