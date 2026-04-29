package com.team27.amazon.billing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // Store any object in Redis with a TTL in minutes
    public void set(String key, Object value, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis SET failed for key {}: {}", key, e.getMessage());
        }
    }

    // Retrieve a simple object (single entity like Transaction, Voucher)
    public <T> T get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            return objectMapper.convertValue(value, type);
        } catch (Exception e) {
            log.warn("Redis GET failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    // Retrieve a complex generic type (like List<Transaction>, List<VoucherUsageDTO>)
    public <T> T get(String key, TypeReference<T> typeRef) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;
            return objectMapper.convertValue(value, typeRef);
        } catch (Exception e) {
            log.warn("Redis GET (TypeRef) failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    // Delete one specific key
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis DELETE failed for key {}: {}", key, e.getMessage());
        }
    }

    // Delete all keys matching a wildcard pattern (e.g. "billing-service::S5-F1::*")
    public void deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis DELETE by pattern failed for {}: {}", pattern, e.getMessage());
        }
    }

    // Build a consistent hash string from query parameters to use as part of cache key
    // e.g. buildParamHash("COMPLETED", "2026-01-01", "2026-12-31") → some integer string
    public String buildParamHash(Object... params) {
        StringBuilder sb = new StringBuilder();
        for (Object p : params) {
            sb.append(p == null ? "null" : p.toString()).append("_");
        }
        return String.valueOf(sb.toString().hashCode());
    }
}