package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.product.exception.ProductReviewNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.repository.ProductReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import com.team27.amazon.product.cache.ProductCacheInvalidator;
import com.team27.amazon.product.cache.ProductCacheKeys;
import com.team27.amazon.product.cache.RedisCacheService;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductReviewService extends AbstractEventSubject {

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    @Qualifier("productReviewEventLogger")
    private MongoEventLogger mongoEventLogger;

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ProductCacheInvalidator productCacheInvalidator;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    @Transactional
    public ProductReview createReview(Long productId, ProductReviewRequest request) {
        Product product = productService.getProductById(productId);

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setUserId(request.getUserId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setVerified(false);
        review.setMetadata(new HashMap<>());

        ProductReview savedReview = productReviewRepository.save(review);
        notifyObservers("REVIEW_ADDED", productReviewEventPayload(productId, savedReview.getId(), Map.of(
            "userId", savedReview.getUserId(),
            "rating", savedReview.getRating(),
            "details", reviewDetails(savedReview)
        )));
        productCacheInvalidator.invalidateProductReview(savedReview.getId(), productId);
        return savedReview;
    }

    public List<ProductReview> getAllReviews() {
        return productReviewRepository.findAll();
    }

    public ProductReview getReviewById(Long id) {
        String cacheKey = ProductCacheKeys.productReviewDetail(id);

        return redisCacheService.getOrLoad(
                cacheKey,
                Duration.ofMinutes(15),
                new TypeReference<ProductReview>() {},
                () -> productReviewRepository.findById(id)
                        .orElseThrow(() -> new ProductReviewNotFoundException(id))
        );
    }

    public List<ProductReview> getReviewsByProductId(Long productId) {
        return productReviewRepository.findByProductId(productId);
    }

    @Transactional
    public ProductReview updateReview(Long id, ProductReviewRequest request) {
        ProductReview existing = getReviewById(id);
        existing.setUserId(request.getUserId());
        existing.setRating(request.getRating());
        existing.setTitle(request.getTitle());
        existing.setComment(request.getComment());
        ProductReview savedReview = productReviewRepository.save(existing);
        Long productId = savedReview.getProduct() == null ? null : savedReview.getProduct().getId();
        notifyObservers("REVIEW_UPDATED", productReviewEventPayload(savedReview.getProduct() == null ? null : savedReview.getProduct().getId(), savedReview.getId(), Map.of(
                "userId", savedReview.getUserId(),
                "rating", savedReview.getRating(),
                "details", reviewDetails(savedReview)
        )));
        productCacheInvalidator.invalidateProductReview(savedReview.getId(), productId);
        return savedReview;
    }

    @Transactional
    public void deleteReview(Long id) {
        ProductReview existing = getReviewById(id);
        Long productId = existing.getProduct() == null ? null : existing.getProduct().getId();

        productReviewRepository.delete(existing);

        notifyObservers("REVIEW_DELETED", productReviewEventPayload(productId, id, Map.of()));

        productCacheInvalidator.invalidateProductReview(id, productId);
    }

    private Map<String, Object> productReviewEventPayload(Long productId, Long reviewId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        if (productId != null) {
            payload.put("productId", productId);
        }
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
