package com.team27.amazon.product.cache;

import org.springframework.stereotype.Service;

@Service
public class ProductCacheInvalidator {

    private final RedisCacheService redisCacheService;

    public ProductCacheInvalidator(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    public void invalidateProduct(Long productId) {
        if (productId != null) {
            redisCacheService.evict(ProductCacheKeys.productDetail(productId));
        }

        invalidateAllProductFeatureCaches();
    }

    public void invalidateProductReview(Long reviewId, Long productId) {
        if (reviewId != null) {
            redisCacheService.evict(ProductCacheKeys.productReviewDetail(reviewId));
        }

        if (productId != null) {
            redisCacheService.evict(ProductCacheKeys.productDetail(productId));
        }

        invalidateReviewAffectedFeatureCaches();
    }

    public void invalidateAllProductFeatureCaches() {
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F1"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F3"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F5"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F6"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F9"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F12"));

    }

    public void invalidateReviewAffectedFeatureCaches() {
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F6"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F9"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F12"));

    }
}