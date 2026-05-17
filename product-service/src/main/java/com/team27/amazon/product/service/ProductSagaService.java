package com.team27.amazon.product.service;

import com.team27.amazon.product.cache.ProductCacheInvalidator;
import com.team27.amazon.product.cache.ProductCacheKeys;
import com.team27.amazon.product.cache.RedisCacheService;
import com.team27.amazon.product.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductSagaService {

    private static final Logger log = LoggerFactory.getLogger(ProductSagaService.class);

    private final ProductRepository productRepository;
    private final ProductCacheInvalidator productCacheInvalidator;
    private final RedisCacheService redisCacheService;

    public ProductSagaService(ProductRepository productRepository,
                              ProductCacheInvalidator productCacheInvalidator,
                              RedisCacheService redisCacheService) {
        this.productRepository = productRepository;
        this.productCacheInvalidator = productCacheInvalidator;
        this.redisCacheService = redisCacheService;
    }

    @Transactional
    public void deductStock(Long orderId, Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            log.warn("Skipping invalid stock deduction orderId={}, productId={}, quantity={}",
                    orderId, productId, quantity);
            return;
        }

        int updatedRows = productRepository.deductStockIfAvailable(productId, quantity);

        if (updatedRows == 0) {
            log.warn("Stock deduction skipped. Product missing, inactive, or insufficient stock. orderId={}, productId={}, quantity={}",
                    orderId, productId, quantity);
            return;
        }

        productCacheInvalidator.invalidateProduct(productId);

        log.info("Deducted product stock. orderId={}, productId={}, quantity={}",
                orderId, productId, quantity);
    }

    @Transactional
    public void restoreStock(Long orderId, Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            log.warn("Skipping invalid stock restore orderId={}, productId={}, quantity={}",
                    orderId, productId, quantity);
            return;
        }

        int updatedRows = productRepository.restoreStock(productId, quantity);

        if (updatedRows == 0) {
            log.warn("Stock restore skipped. Product missing. orderId={}, productId={}, quantity={}",
                    orderId, productId, quantity);
            return;
        }

        productCacheInvalidator.invalidateProduct(productId);

        log.info("Restored product stock. orderId={}, productId={}, quantity={}",
                orderId, productId, quantity);
    }

    public void invalidateSalesAffectedCaches() {
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F3"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F6"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F9"));
        redisCacheService.evictByPattern(ProductCacheKeys.featurePattern("S2-F12"));
    }
}