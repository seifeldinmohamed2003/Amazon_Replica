package com.team27.amazon.product.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.product.dto.LowStockAlertDTO;
import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.product.dto.ProductReviewVerificationRequest;
import com.team27.amazon.product.dto.ProductSalesDTO;
import com.team27.amazon.product.dto.TopProductDTO;
import com.team27.amazon.product.exception.InvalidPriceRangeException;
import com.team27.amazon.product.exception.InvalidProductAlertException;
import com.team27.amazon.product.exception.InvalidReviewException;
import com.team27.amazon.product.exception.ProductNotFoundException;
import com.team27.amazon.product.exception.ProductReviewNotFoundException;
import com.team27.amazon.product.exception.ReviewVerificationForbiddenException;
import com.team27.amazon.product.exception.UserNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.repository.ProductRepository;
import com.team27.amazon.product.repository.ProductReviewRepository;

import jakarta.transaction.Transactional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public List<Product> getProducts(ProductStatus status, String category) {
        if (status != null) {
            return productRepository.findByStatus(status);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategoryIgnoreCase(category);
        }
        return productRepository.findAll();
    }

    public List<Product> searchProducts(Double minPrice, Double maxPrice, String category) {
        // Validate price range
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new InvalidPriceRangeException(minPrice, maxPrice);
        }

        // Set default values if not provided
        Double min = minPrice != null ? minPrice : 0.0;
        Double max = maxPrice != null ? maxPrice : Double.MAX_VALUE;

        return productRepository.searchByPriceRange(min, max, category);
    }

    public Product updateProduct(Long id, ProductRequest request) {
        Product existing = getProductById(id);
        applyRequest(existing, request);
        return productRepository.save(existing);
    }

    public Product updateSpecifications(Long id, Map<String, Object> newSpecifications) {
        Product existing = getProductById(id);

        // Merge new specifications with existing ones
        Map<String, Object> currentSpecifications = existing.getSpecifications();
        if (currentSpecifications == null) {
            currentSpecifications = new HashMap<>();
        }

        if (newSpecifications != null) {
            currentSpecifications.putAll(newSpecifications);
        }

        existing.setSpecifications(currentSpecifications);
        return productRepository.save(existing);
    }

    public ProductSalesDTO getProductSalesSummary(Long productId, LocalDate startDate, LocalDate endDate) {
        Product product = getProductById(productId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Object[] result = productRepository.getProductSalesSummary(productId, startDateTime, endDateTime);

        long totalUnitsSold = 0L;
        double totalRevenue = 0.0;

        if (result != null && result.length >= 2) {
            totalUnitsSold = result[0] == null ? 0L : ((Number) result[0]).longValue();
            totalRevenue = result[1] == null ? 0.0 : ((Number) result[1]).doubleValue();
        }

        double averageSellingPrice = totalUnitsSold == 0 ? 0.0 : totalRevenue / totalUnitsSold;

        return new ProductSalesDTO(
                product.getId(),
                product.getName(),
                totalUnitsSold,
                totalRevenue,
                averageSellingPrice
        );
    }

    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);

    }

    @Transactional
    public ProductReview addReview(Long productId, ProductReviewRequest request) {
        Product product = getProductById(productId);

        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewException("Rating must be between 1 and 5.");
        }

        if (!productRepository.userExists(request.getUserId())) {
            throw new UserNotFoundException(request.getUserId());
        }

        if (!productRepository.hasDeliveredPurchase(request.getUserId(), productId)) {
            throw new InvalidReviewException("User must purchase this product before reviewing it.");
        }

        ProductReview review = new ProductReview();
        review.setUserId(request.getUserId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setVerified(false);
        review.setMetadata(new HashMap<>());

        product.addReview(review);
        productReviewRepository.save(review);

        int oldCount = product.getTotalRatings() == null ? 0 : product.getTotalRatings();
        double oldAverage = product.getRating() == null ? 0.0 : product.getRating();

        int newCount = oldCount + 1;
        double newAverage = ((oldAverage * oldCount) + request.getRating()) / newCount;

        product.setTotalRatings(newCount);
        product.setRating(newAverage);

        productRepository.save(product);

        return review;
    }

    @Transactional
    public Product verifyReview(Long productId, Long reviewId, ProductReviewVerificationRequest request) {
        Product product = getProductById(productId);

        ProductReview review = productReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ProductReviewNotFoundException(reviewId));

        if (review.getProduct() == null || review.getProduct().getId() == null) {
            throw new ProductReviewNotFoundException(reviewId);
        }

        if (!review.getProduct().getId().equals(productId)) {
            throw new InvalidReviewException("Review does not belong to the specified product.");
        }

        if (!productRepository.hasDeliveredPurchase(review.getUserId(), productId)) {
            throw new InvalidReviewException("Reviewer does not have a verified purchase for this product.");
        }

        if (!productRepository.userExists(request.getVerifiedBy())) {
            throw new UserNotFoundException(request.getVerifiedBy());
        }

        if (!productRepository.isAdminUser(request.getVerifiedBy())) {
            throw new ReviewVerificationForbiddenException("Only an admin user can verify reviews.");
        }

        review.setVerified(true);

        Map<String, Object> metadata = review.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        } else {
            metadata = new HashMap<>(metadata);
        }

        metadata.put("verifiedAt", LocalDateTime.now().toString());
        metadata.put("verifiedBy", request.getVerifiedBy());
        review.setMetadata(metadata);

        productReviewRepository.save(review);
        return product;
    }

    public List<LowStockAlertDTO> getLowStockAlerts(Integer threshold) {
        if (threshold == null || threshold < 0) {
            throw new InvalidProductAlertException("Threshold must be zero or greater.");        }

        return productRepository.findByStockQuantityLessThanOrderByStockQuantityAsc(threshold)
                .stream()
                .map(LowStockAlertDTO::from)
                .toList();
    }
    public List<Product> searchBySpecification(String key, String value, ProductStatus status) {
    if (key == null || key.isBlank() || value == null || value.isBlank()) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Key and value must not be blank"
        );
    }

    return productRepository.findBySpecificationKeyValueAndOptionalStatus(
            key,
            value,
            status == null ? null : status.name()
    );
} 
        
    public List<TopProductDTO> getTopRatedProducts(Integer limit) {
    if (limit == null || limit <= 0) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Limit must be greater than 0"
        );
    }

    List<Object[]> rows = productRepository.findTopRatedProducts(limit);

    return rows.stream()
            .map(row -> new TopProductDTO(
                    ((Number) row[0]).longValue(),
                    (String) row[1],
                    row[2] == null ? 0.0 : ((Number) row[2]).doubleValue(),
                    row[3] == null ? 0L : ((Number) row[3]).longValue()
            ))
            .toList();
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setStockQuantity(request.getStockQuantity());
        product.setStatus(request.getStatus() == null ? ProductStatus.ACTIVE : request.getStatus());

        Map<String, Object> specifications = request.getSpecifications();
        product.setSpecifications(specifications == null ? new HashMap<>() : new HashMap<>(specifications));
    }

    @Transactional
    public Product discontinueProduct(Long productId) {
    Product product = getProductById(productId);

    boolean existsInPendingOrders = productRepository.existsInPendingOrders(productId);
    if (existsInPendingOrders) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot discontinue product because it is used in pending orders"
        );
    }

    product.setStatus(ProductStatus.INACTIVE);
    return productRepository.save(product);
}
    
}
