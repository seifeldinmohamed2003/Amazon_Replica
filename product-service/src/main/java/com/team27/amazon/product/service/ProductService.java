package com.team27.amazon.product.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

@Service
public class ProductService extends AbstractEventSubject {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    @Qualifier("productEventLogger")
    private MongoEventLogger mongoEventLogger;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        Product savedProduct = productRepository.save(product);
        notifyObservers("PRODUCT_CREATED", productEventPayload(savedProduct.getId(), Map.of(
                "name", savedProduct.getName(),
                "status", savedProduct.getStatus() == null ? null : savedProduct.getStatus().name()
        )));
        return savedProduct;
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
        Product savedProduct = productRepository.save(existing);
        notifyObservers("PRODUCT_UPDATED", productEventPayload(savedProduct.getId(), Map.of(
                "name", savedProduct.getName(),
                "status", savedProduct.getStatus() == null ? null : savedProduct.getStatus().name()
        )));
        return savedProduct;
    }

    public Product updateSpecifications(Long id, Map<String, Object> newSpecifications) {
        Product existing = getProductById(id);

        // Merge new specifications with existing ones
        Map<String, Object> currentSpecifications = existing.getSpecifications();
        if (currentSpecifications == null) {
            currentSpecifications = new HashMap<>();
        } else {
            currentSpecifications = new HashMap<>(currentSpecifications);
        }

        if (newSpecifications != null) {
            Object nestedSpecifications = newSpecifications.get("specifications");
            if (nestedSpecifications instanceof Map<?, ?> nestedMap) {
                for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                    currentSpecifications.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            } else {
                currentSpecifications.putAll(newSpecifications);
            }
        }

        existing.setSpecifications(currentSpecifications);
        Product savedProduct = productRepository.save(existing);
        notifyObservers("SPECIFICATIONS_UPDATED", productEventPayload(savedProduct.getId(), Map.of(
            "details", new HashMap<>(currentSpecifications)
        )));
        return savedProduct;
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

        return ProductSalesDTO.builder()
                .productId(product.getId())
                .name(product.getName())
                .totalUnitsSold(totalUnitsSold)
                .totalRevenue(totalRevenue)
                .averageSellingPrice(averageSellingPrice)
                .build();
    }

    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
        notifyObservers("PRODUCT_DELETED", productEventPayload(id, Map.of()));

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

        ProductReview savedReview = productReviewRepository.save(review);
        product.addReview(savedReview);

        int oldCount = product.getTotalRatings() == null ? 0 : product.getTotalRatings();
        double oldAverage = product.getRating() == null ? 0.0 : product.getRating();

        int newCount = oldCount + 1;
        double newAverage = ((oldAverage * oldCount) + request.getRating()) / newCount;

        product.setTotalRatings(newCount);
        product.setRating(newAverage);

        productRepository.save(product);
        notifyObservers("REVIEW_ADDED", productReviewEventPayload(product.getId(), savedReview.getId(), Map.of(
            "userId", savedReview.getUserId(),
            "rating", savedReview.getRating(),
            "details", reviewDetails(savedReview)
        )));

        return savedReview;
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
        notifyObservers("REVIEW_VERIFIED", productReviewEventPayload(productId, reviewId, Map.of(
            "verifiedBy", request.getVerifiedBy(),
            "details", new HashMap<>(metadata)
        )));
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
            .map(row -> TopProductDTO.builder()
                    .productId(((Number) row[0]).longValue())
                    .name((String) row[1])
                    .rating(row[2] == null ? 0.0 : ((Number) row[2]).doubleValue())
                    .totalSales(row[3] == null ? 0L : ((Number) row[3]).longValue())
                    .build()
            )
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
    Product savedProduct = productRepository.save(product);
    notifyObservers("STATUS_CHANGED", productEventPayload(savedProduct.getId(), Map.of(
            "status", savedProduct.getStatus() == null ? null : savedProduct.getStatus().name()
    )));
    return savedProduct;
}

    private Map<String, Object> productEventPayload(Long productId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private Map<String, Object> productReviewEventPayload(Long productId, Long reviewId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", productId);
        if (reviewId != null) {
            payload.put("reviewId", reviewId);
        }
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private Map<String, Object> reviewDetails(ProductReview review) {
        Map<String, Object> details = new HashMap<>();
        if (review == null) {
            return details;
        }

        details.put("userId", review.getUserId());
        details.put("rating", review.getRating());
        details.put("title", review.getTitle());
        return details;
    }
    
}
