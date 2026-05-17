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
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.product.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.product.dto.ProductCatalogDashboardDTO;
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
import com.team27.amazon.contracts.dto.ProductSalesAggregateDTO;
import com.team27.amazon.product.messaging.publishers.ProductEventPublisher;

import java.time.Duration;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import com.team27.amazon.contracts.feign.OrderServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.contracts.dto.UserDTO;
import feign.FeignException;

@Service
public class ProductService extends AbstractEventSubject {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private ProductEventPublisher productEventPublisher;

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

    @Autowired(required = false)
    private OrderServiceClient orderServiceClient;

    @Autowired(required = false)
    private UserServiceClient userServiceClient;

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

    // S2-READ-DB helper: lightweight existence check
    public boolean productExists(Long id) {
        return productRepository.existsById(id);
    }

    // S2-READ-DB helper: batch product lookup
    public List<Product> getProductsBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productRepository.findAllById(ids).stream().toList();
    }

    // Feign-safe user existence check using UserServiceClient
    public boolean remoteUserExists(Long userId) {
        if (userServiceClient == null) {
            return false;
        }
        try {
            UserDTO user = userServiceClient.getUser(userId);
            return user != null;
        } catch (FeignException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("UserService call failed, treating as not found: {}", userId, e);
            return false;
        }
    }

    // Feign-safe wrapper to ask OrderService if a user has purchased a product
    public boolean hasUserPurchasedProductViaOrderService(Long userId, Long productId) {
        if (orderServiceClient == null) {
            return false;
        }
        try {
            return orderServiceClient.hasUserPurchasedProduct(userId, productId);
        } catch (FeignException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("OrderService call failed for hasUserPurchasedProduct userId={} productId={}", userId, productId, e);
            return false;
        }
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

    public List<Product> searchProductsFullText(
            String query,
            String category,
            String brand,
            ProductStatus status,
            Double minPrice,
            Double maxPrice,
            Double minRating,
            Double maxRating
    ) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }

        validateRange("price", minPrice, maxPrice);
        validateRange("rating", minRating, maxRating);

        String cacheKey = ProductCacheKeys.s2f10FullText(
                query,
                normalizeOptional(category),
                normalizeOptional(brand),
                status == null ? null : status.name(),
                minPrice,
                maxPrice,
                minRating,
                maxRating
        );

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(5),
                new TypeReference<List<Product>>() {},
                () -> searchFullTextFromElasticsearch(query, category, brand, status, minPrice, maxPrice, minRating, maxRating)
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

    private ProductSalesAggregateDTO getProductSalesFromOrderService(Long productId,
                                                                     LocalDate startDate,
                                                                     LocalDate endDate) {
        if (orderServiceClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service client is not available"
            );
        }

        try {
            return orderServiceClient.getProductSales(
                    productId,
                    startDate.toString(),
                    endDate.toString()
            );
        } catch (FeignException.NotFound ex) {
            return new ProductSalesAggregateDTO(0L, 0.0, 0.0);
        } catch (FeignException ex) {
            log.warn("Order service failed while loading product sales. productId={}", productId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service temporarily unavailable"
            );
        }
    }

    public ProductSalesDTO getProductSalesSummary(Long productId, LocalDate startDate, LocalDate endDate) {
        String cacheKey = ProductCacheKeys.s2f3Sales(productId, startDate, endDate);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(10),
                new TypeReference<ProductSalesDTO>() {},
                () -> {
                    Product product = getProductById(productId);

                    ProductSalesAggregateDTO sales = getProductSalesFromOrderService(productId, startDate, endDate);

                    return ProductSalesDTO.builder()
                            .productId(product.getId())
                            .name(product.getName())
                            .totalUnitsSold(sales.totalUnitsSold())
                            .totalRevenue(sales.totalRevenue())
                            .averageSellingPrice(sales.averageSellingPrice())
                            .build();
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

        ensureUserExistsViaUserService(request.getUserId());

        if (!hasPurchasedViaOrderService(request.getUserId(), productId)) {
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

int oldCount = product.getTotalRatings() == null ? 0 : product.getTotalRatings();
double oldAverage = product.getRating() == null ? 0.0 : product.getRating();

int newCount = oldCount + 1;
double newAverage = ((oldAverage * oldCount) + request.getRating()) / newCount;

product.setTotalRatings(newCount);
product.setRating(newAverage);

Product savedProduct = productRepository.save(product);

ProductReview savedReview = savedProduct.getProductReviews()
        .stream()
        .filter(r -> r.getUserId().equals(request.getUserId())
                && r.getRating().equals(request.getRating())
                && r.getTitle().equals(request.getTitle())
                && r.getComment().equals(request.getComment()))
        .reduce((first, second) -> second)
        .orElse(review);

        notifyObservers("REVIEW_ADDED", productReviewEventPayload(product.getId(), savedReview.getId(), Map.of(
            "userId", savedReview.getUserId(),
            "rating", savedReview.getRating(),
            "details", reviewDetails(savedReview)
        )));
        productEventPublisher.publishProductReviewAdded(
                savedProduct.getId(),
                savedReview.getId(),
                savedReview.getUserId(),
                savedReview.getRating()
        );

        productEventPublisher.publishProductRated(
                savedProduct.getId(),
                savedProduct.getRating(),
                savedProduct.getTotalRatings()
        );
        productCacheInvalidator.invalidateProductReview(savedReview.getId(), productId);
        return savedReview;
    }
    private void ensureUserExistsViaUserService(Long userId) {
        if (userServiceClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User service client is not available"
            );
        }

        try {
            UserDTO user = userServiceClient.getUser(userId);

            if (user == null || user.id() == null) {
                throw new UserNotFoundException(userId);
            }
        } catch (FeignException.NotFound ex) {
            throw new UserNotFoundException(userId);
        } catch (FeignException ex) {
            log.warn("User service failed while checking user. userId={}", userId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable"
            );
        }
    }

    private boolean hasPurchasedViaOrderService(Long userId, Long productId) {
        if (orderServiceClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service client is not available"
            );
        }

        try {
            return orderServiceClient.hasUserPurchasedProduct(userId, productId);
        } catch (FeignException.NotFound ex) {
            return false;
        } catch (FeignException ex) {
            log.warn("Order service failed while checking purchase. userId={}, productId={}",
                    userId, productId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service temporarily unavailable"
            );
        }
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

        if (!hasPurchasedViaOrderService(review.getUserId(), productId)) {
            throw new InvalidReviewException("Reviewer does not have a verified purchase for this product.");
        }

        UserDTO verifier = getUserViaUserService(request.getVerifiedBy());

        if (verifier.role() == null || !"ADMIN".equalsIgnoreCase(verifier.role())) {
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

    private UserDTO getUserViaUserService(Long userId) {
        if (userServiceClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User service client is not available"
            );
        }

        try {
            UserDTO user = userServiceClient.getUser(userId);

            if (user == null || user.id() == null) {
                throw new UserNotFoundException(userId);
            }

            return user;
        } catch (FeignException.NotFound ex) {
            throw new UserNotFoundException(userId);
        } catch (FeignException ex) {
            log.warn("User service failed while loading user. userId={}", userId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable"
            );
        }
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
                        .map(product -> {
                            LowStockAlertDTO dto = LowStockAlertDTO.from(product);
                            int recentSalesCount = getRecentSalesCountFromOrderService(product.getId(), 30);

                            dto.setAlertMessage(
                                    dto.getAlertMessage()
                                            + " Recent sales in last 30 days: "
                                            + recentSalesCount
                            );

                            return dto;
                        })
                        .toList()
        );
    }

    private int getRecentSalesCountFromOrderService(Long productId, int days) {
        if (orderServiceClient == null) {
            return 0;
        }

        try {
            return orderServiceClient.getRecentSalesCount(productId, days);
        } catch (FeignException.NotFound ex) {
            return 0;
        } catch (FeignException ex) {
            log.warn("Order service failed while loading recent sales count. productId={}, days={}",
                    productId, days, ex);
            return 0;
        }
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
                () -> productRepository.findTopRatedProductEntities(limit)
                        .stream()
                        .map(product -> TopProductDTO.builder()
                                .productId(product.getId())
                                .name(product.getName())
                                .rating(product.getRating())
                                .totalSales(getUnitsSoldFromOrderService(product.getId()))
                                .build())
                        .toList()
        );
    }

    private Long getUnitsSoldFromOrderService(Long productId) {
        if (orderServiceClient == null) {
            return 0L;
        }

        try {
            return orderServiceClient.getUnitsSold(productId);
        } catch (FeignException.NotFound ex) {
            return 0L;
        } catch (FeignException ex) {
            log.warn("Order service failed while loading units sold. productId={}", productId, ex);
            return 0L;
        }
    }

        public ProductCatalogDashboardDTO getProductCatalogDashboard() {
        notifyObservers("DASHBOARD_VIEWED", productEventPayload(null, Map.of(
                "dashboard", "ProductCatalogDashboard",
                "featureId", "S2-F12"
        )));

        String cacheKey = ProductCacheKeys.s2f12CatalogDashboard();

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(10),
                new TypeReference<ProductCatalogDashboardDTO>() {},
                () -> {
                    Long totalProducts = productRepository.countAllProductsForDashboard();
                    Long outOfStockCount = productRepository.countOutOfStockProductsForDashboard();
                    Double averageRating = productRepository.averageRatedProductsForDashboard();
                    Double averagePrice = productRepository.averagePriceForDashboard();
                    Long lowStockCount = productRepository.countLowStockActiveProductsForDashboard();

                    Map<String, Long> categoryDistribution = new HashMap<>();
                    List<Object[]> categoryRows = productRepository.countProductsByCategoryForDashboard();

                    for (Object[] row : categoryRows) {
                        if (row == null || row.length < 2) {
                            continue;
                        }

                        String category = row[0] == null ? "UNKNOWN" : String.valueOf(row[0]);
                        Long count = row[1] == null ? 0L : ((Number) row[1]).longValue();

                        categoryDistribution.put(category, count);
                    }

                    return ProductCatalogDashboardDTO.builder()
                            .totalProducts(totalProducts)
                            .outOfStockCount(outOfStockCount)
                            .averageRating(averageRating)
                            .categoryDistribution(categoryDistribution)
                            .averagePrice(averagePrice)
                            .lowStockCount(lowStockCount)
                            .build();
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

        int pendingOrderCount = getPendingOrderCountFromOrderService(productId);

        if (pendingOrderCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot discontinue product because it is used in pending orders"
            );
        }

        String oldStatus = product.getStatus() == null ? null : product.getStatus().name();

        product.setStatus(ProductStatus.INACTIVE);

        Product savedProduct = productRepository.save(product);

        String newStatus = savedProduct.getStatus() == null ? null : savedProduct.getStatus().name();

        productEventPublisher.publishProductDiscontinued(
                savedProduct.getId(),
                oldStatus,
                newStatus
        );

        notifyObservers("STATUS_CHANGED", productEventPayload(savedProduct.getId(), Map.of(
                "oldStatus", oldStatus,
                "newStatus", newStatus
        )));

        productCacheInvalidator.invalidateProduct(productId);

        return savedProduct;
    }

    private int getPendingOrderCountFromOrderService(Long productId) {
        if (orderServiceClient == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service client is not available"
            );
        }

        try {
            return orderServiceClient.getPendingOrderCountForProduct(productId);
        } catch (FeignException.NotFound ex) {
            return 0;
        } catch (FeignException ex) {
            log.warn("Order service failed while checking pending orders. productId={}", productId, ex);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Order service temporarily unavailable"
            );
        }
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

    private List<Product> searchFullTextFromElasticsearch(
            String query,
            String category,
            String brand,
            ProductStatus status,
            Double minPrice,
            Double maxPrice,
            Double minRating,
            Double maxRating
    ) {
        try {
            Map<String, Object> boolQuery = new HashMap<>();

            List<Map<String, Object>> mustClauses = new ArrayList<>();
            mustClauses.add(Map.of(
                    "multi_match", Map.of(
                            "query", query,
                            "fields", List.of("name^2", "description"),
                            "operator", "or"
                    )
            ));
            boolQuery.put("must", mustClauses);

            String wildcardText = "*" + query.trim().toLowerCase() + "*";
            List<Map<String, Object>> shouldClauses = new ArrayList<>();
            shouldClauses.add(Map.of(
                    "wildcard", Map.of(
                            "name", Map.of(
                                    "value", wildcardText,
                                    "case_insensitive", true,
                                    "boost", 2.0
                            )
                    )
            ));
            shouldClauses.add(Map.of(
                    "wildcard", Map.of(
                            "description", Map.of(
                                    "value", wildcardText,
                                    "case_insensitive", true
                            )
                    )
            ));
            boolQuery.put("should", shouldClauses);

            List<Map<String, Object>> filterClauses = buildFullTextFilters(category, brand, status, minPrice, maxPrice, minRating, maxRating);
            if (!filterClauses.isEmpty()) {
                boolQuery.put("filter", filterClauses);
            }

            Map<String, Object> requestBody = Map.of(
                    "query", Map.of("bool", boolQuery),
                    "sort", List.of(Map.of("_score", Map.of("order", "desc"))),
                    "size", 200
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(elasticsearchUri + "/products/_search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Elasticsearch full-text search failed: " + response.statusCode() + " " + response.body());
            }

            return mapProductsByRelevance(response.body());
        } catch (Exception ex) {
            log.warn("Failed to run full-text product search", ex);
            return List.of();
        }
    }

    private List<Map<String, Object>> buildFullTextFilters(
            String category,
            String brand,
            ProductStatus status,
            Double minPrice,
            Double maxPrice,
            Double minRating,
            Double maxRating
    ) {
        List<Map<String, Object>> filters = new ArrayList<>();

        if (normalizeOptional(category) != null) {
            filters.add(Map.of("term", Map.of("category", category.trim().toUpperCase())));
        }
        if (normalizeOptional(brand) != null) {
            filters.add(Map.of("term", Map.of("brand", brand.trim())));
        }
        if (status != null) {
            filters.add(Map.of("term", Map.of("status", status.name())));
        }
        if (minPrice != null || maxPrice != null) {
            Map<String, Object> priceRange = new HashMap<>();
            if (minPrice != null) {
                priceRange.put("gte", minPrice);
            }
            if (maxPrice != null) {
                priceRange.put("lte", maxPrice);
            }
            filters.add(Map.of("range", Map.of("price", priceRange)));
        }
        if (minRating != null || maxRating != null) {
            Map<String, Object> ratingRange = new HashMap<>();
            if (minRating != null) {
                ratingRange.put("gte", minRating);
            }
            if (maxRating != null) {
                ratingRange.put("lte", maxRating);
            }
            filters.add(Map.of("range", Map.of("rating", ratingRange)));
        }

        return filters;
    }

    private List<Product> mapProductsByRelevance(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode hitNodes = root.path("hits").path("hits");

        if (!hitNodes.isArray() || hitNodes.isEmpty()) {
            return List.of();
        }

        List<Long> orderedIds = new ArrayList<>();
        for (JsonNode hit : hitNodes) {
            JsonNode productIdNode = hit.path("_source").path("productId");
            if (productIdNode.isNumber()) {
                orderedIds.add(productIdNode.asLong());
            }
        }

        if (orderedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productById = productRepository.findAllById(orderedIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        List<Product> orderedProducts = new ArrayList<>();
        for (Long id : orderedIds) {
            Product product = productById.get(id);
            if (product != null) {
                orderedProducts.add(product);
            }
        }

        return orderedProducts;
    }

    private void validateRange(String fieldName, Double min, Double max) {
        if (min != null && max != null && min > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + " range: min value must be less than or equal to max value"
            );
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
