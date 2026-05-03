package com.team27.amazon.user.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class CacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final RedisConnectionFactory connectionFactory;
    private final RedisCacheService redisCacheService;

    public CacheInvalidationService(
            RedisConnectionFactory connectionFactory,
            RedisCacheService redisCacheService
    ) {
        this.connectionFactory = connectionFactory;
        this.redisCacheService = redisCacheService;
    }

    public void invalidateUserDetail(Long userId) {
        redisCacheService.delete(CacheKeyBuilder.entityKey(CacheConstants.ENTITY_USER, userId));
    }

    public void invalidateShippingAddressDetail(Long addressId) {
        redisCacheService.delete(CacheKeyBuilder.entityKey(CacheConstants.ENTITY_SHIPPING_ADDRESS, addressId));
    }

    public void invalidateAllUserServiceFeatureCaches() {
        for (String pattern : CacheConstants.USER_FEATURE_CACHE_PATTERNS) {
            deleteByPattern(pattern);
        }
    }

    public void invalidateUserWriteCaches(Long userId) {
        invalidateUserDetail(userId);
        invalidateAllUserServiceFeatureCaches();
    }

    public void invalidateShippingAddressWriteCaches(Long addressId, Long userId) {
        invalidateShippingAddressDetail(addressId);

        if (userId != null) {
            invalidateUserDetail(userId);
        }

        invalidateAllUserServiceFeatureCaches();
    }

    public void deleteByPattern(String pattern) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] key = cursor.next();
                    connection.del(key);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete Redis keys by pattern {}", pattern);
        }
    }
}