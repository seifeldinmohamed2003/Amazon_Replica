package com.team27.amazon.product.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public <T> T getOrLoad(
            String key,
            Duration ttl,
            TypeReference<T> typeReference,
            Supplier<T> databaseLoader
    ) {
        try {
            String cachedJson = redisTemplate.opsForValue().get(key);

            if (cachedJson != null) {
                return objectMapper.readValue(cachedJson, typeReference);
            }

            T value = databaseLoader.get();
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
            return value;

        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unavailable. Falling back to PostgreSQL for key {}", key);
            return databaseLoader.get();

        } catch (Exception ex) {
            log.warn("Redis cache error for key {}. Falling back to PostgreSQL", key, ex);
            return databaseLoader.get();
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Failed to evict Redis key {}", key, ex);
        }
    }

    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("Failed to evict Redis keys by pattern {}", pattern, ex);
        }
    }
}