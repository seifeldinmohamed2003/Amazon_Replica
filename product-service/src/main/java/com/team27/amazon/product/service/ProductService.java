package com.team27.amazon.product.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.product.adapter.ObjectArrayDtoAdapter;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.team27.amazon.product.cache.ProductCacheInvalidator;
import com.team27.amazon.product.cache.ProductCacheKeys;
import com.team27.amazon.product.cache.RedisCacheService;

import java.time.Duration;
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

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ProductCacheInvalidator productCacheInvalidator;

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    @Autowired
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @Value("${spring.elasticsearch.uris:http://elasticsearch:9200}")
private String elasticsearchUri;

private final ObjectMapper objectMapper = new ObjectMapper();
private final HttpClient httpClient = HttpClient.newHttpClient();



    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        Product savedProduct = productRepository.save(product);
autoIndexProduct(savedProduct, "auto_crud_create");
        notifyObservers("PRODUCT_CREATED", productEventPayload(savedProduct.getId(), Map.of(
                "name", savedProduct.getName(),
                "status", savedProduct.getStatus() == null ? null : savedProduct.getStatus().name()
        )));

        productCacheInvalidator.invalidateAllProductFeatureCaches();

        return savedProduct;
    }

    public Product getProductById(Long id) {
        String cacheKey = ProductCacheKeys.productDetail(id);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(15),
                new TypeReference<Product>() {},
                () -> productRepository.findById(id)
                        .orElseThrow(() -> new ProductNotFoundException(id))
        );
    }

    public void indexProductForSearch(Long id) {
        Product product = getProductById(id);
        autoIndexProduct(product, "explicit");
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

        String cacheKey = ProductCacheKeys.s2f1Search(category, min, max);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(5),
                new TypeReference<List<Product>>() {},
                () -> productRepository.searchByPriceRange(min, max, category)
        );
    }

    public Product updateProduct(Long id, ProductRequest request) {
        Product existing = getProductById(id);
        applyRequest(existing, request);
        Product savedProduct = productRepository.save(existing);
        autoIndexProduct(savedProduct, "auto_crud_update");
        notifyObservers("PRODUCT_UPDATED", productEventPayload(savedProduct.getId(), Map.of(
                "name", savedProduct.getName(),
                "status", savedProduct.getStatus() == null ? null : savedProduct.getStatus().name()
        )));

        productCacheInvalidator.invalidateProduct(id);

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

        productCacheInvalidator.invalidateProduct(id);

        return savedProduct;
    }

    public ProductSalesDTO getProductSalesSummary(Long productId, LocalDate startDate, LocalDate endDate) {
        String cacheKey = ProductCacheKeys.s2f3Sales(productId, startDate, endDate);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(10),
                new TypeReference<ProductSalesDTO>() {
                },
                () -> {
                    Product product = getProductById(productId);

                    LocalDateTime startDateTime = startDate.atStartOfDay();
                    LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

                    Object[] result = productRepository.getProductSalesSummary(productId, startDateTime, endDateTime);

                    return objectArrayDtoAdapter.toProductSalesDTO(product.getId(), product.getName(), result);
                }
        );
    }
    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
        autoDeleteProductFromIndex(id);
        notifyObservers("PRODUCT_DELETED", productEventPayload(id, Map.of("productId", id,
                "source", "auto_crud_delete")));

        productCacheInvalidator.invalidateProduct(id);
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
        productCacheInvalidator.invalidateProductReview(savedReview.getId(), productId);
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
    @Transactional
    public List<LowStockAlertDTO> getLowStockAlerts(Integer threshold) {
        if (threshold == null || threshold < 0) {
            throw new InvalidProductAlertException("Threshold must be zero or greater.");
        }

        String cacheKey = ProductCacheKeys.s2f9LowStock(threshold);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(10),
                new TypeReference<List<LowStockAlertDTO>>() {},
                () -> productRepository.findLowStockProducts(threshold)
                        .stream()
                        .map(LowStockAlertDTO::from)
                        .toList()
        );
    }
    public List<Product> searchBySpecification(String key, String value, ProductStatus status) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Key and value must not be blank"
            );
        }

        String cacheKey = ProductCacheKeys.s2f5Specifications(
                key,
                value,
                status == null ? null : status.name()
        );

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(5),
                new TypeReference<List<Product>>() {},
                () -> productRepository.findBySpecificationKeyValueAndOptionalStatus(
                        key,
                        value,
                        status == null ? null : status.name()
                )
        );
    }

    public List<TopProductDTO> getTopRatedProducts(Integer limit) {
        if (limit == null || limit <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Limit must be greater than 0"
            );
        }

        String cacheKey = ProductCacheKeys.s2f6TopRated(limit);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(10),
                new TypeReference<List<TopProductDTO>>() {},
                () -> {
                    List<Object[]> rows = productRepository.findTopRatedProducts(limit);

                    return rows.stream()
                            .map(objectArrayDtoAdapter::toTopProductDTO)
                            .toList();
                }
        );
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
        productCacheInvalidator.invalidateProduct(productId);
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

private void autoIndexProduct(Product product, String source) {
    try {
        Map<String, Object> document = new HashMap<>();
        document.put("id", String.valueOf(product.getId()));
        document.put("productId", product.getId());
        document.put("name", product.getName());
        document.put("description", product.getDescription());
        document.put("category", product.getCategory());
        document.put("brand", product.getBrand());
        document.put("price", product.getPrice());
        document.put("stockQuantity", product.getStockQuantity());
        document.put("rating", product.getRating());
        document.put("status", product.getStatus() == null ? null : product.getStatus().name());

        createProductsIndexIfNeeded();

        String jsonBody = objectMapper.writeValueAsString(document);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchUri + "/products/_doc/" + product.getId()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            throw new IllegalStateException("Elasticsearch indexing failed: " + response.statusCode() + " " + response.body());
        }

        notifyObservers("INDEXED", productEventPayload(product.getId(), Map.of(
                "productId", product.getId(),
                "indexedFields", List.of(
                        "id",
                        "name",
                        "description",
                        "category",
                        "brand",
                        "price",
                        "stockQuantity",
                        "rating",
                        "status"
                ),
                "source", source
        )));
        redisCacheService.evictByPattern("product-service::S2-F10::*");
    } catch (Exception e) {
        log.warn("Failed to auto-index product {}", product.getId(), e);
    }
}

private void createProductsIndexIfNeeded() {
    try {
        String mapping = """
                {
                  "mappings": {
                    "properties": {
                      "id": { "type": "keyword" },
                      "productId": { "type": "long" },
                      "name": { "type": "text" },
                      "description": { "type": "text" },
                      "category": { "type": "keyword" },
                      "brand": { "type": "keyword" },
                      "price": { "type": "double" },
                      "stockQuantity": { "type": "integer" },
                      "rating": { "type": "double" },
                      "status": { "type": "keyword" }
                    }
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchUri + "/products"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(mapping))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 400) {
            throw new IllegalStateException("Elasticsearch index creation failed: " + response.statusCode() + " " + response.body());
        }
    } catch (Exception e) {
        log.warn("Could not create Elasticsearch products index", e);
    }
}

private void autoDeleteProductFromIndex(Long productId) {
    try {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(elasticsearchUri + "/products/_doc/" + productId))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300 && response.statusCode() != 404) {
            throw new IllegalStateException("Elasticsearch delete failed: "
                    + response.statusCode() + " " + response.body());
        }

        redisCacheService.evictByPattern("product-service::S2-F10::*");
    } catch (Exception e) {
        log.warn("Failed to delete product {} from Elasticsearch index", productId, e);
    }
}
}
