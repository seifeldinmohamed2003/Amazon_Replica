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

    // ✅ ADD ONLY
    public static Builder builder() {
        return new Builder();
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Long getTotalSales() { return totalSales; }
    public void setTotalSales(Long totalSales) { this.totalSales = totalSales; }

    // ✅ ADD ONLY
    public static class Builder {
        private Long productId;
        private String name;
        private Double rating;
        private Long totalSales;

        public Builder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder rating(Double rating) {
            this.rating = rating;
            return this;
        }

        public Builder totalSales(Long totalSales) {
            this.totalSales = totalSales;
            return this;
        }

        public TopProductDTO build() {
            return new TopProductDTO(
                    productId,
                    name,
                    rating,
                    totalSales
            );
        }
    }
}