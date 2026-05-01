package com.team27.amazon.user.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T getOrCompute(String key, Class<T> type, Duration ttl, Supplier<T> dbSupplier) {
        Optional<T> cached = get(key, type);

        if (cached.isPresent()) {
            return cached.get();
        }

        T value = dbSupplier.get();
        put(key, value, ttl);
        return value;
    }

    public <T> T getOrCompute(String key, TypeReference<T> type, Duration ttl, Supplier<T> dbSupplier) {
        Optional<T> cached = get(key, type);

        if (cached.isPresent()) {
            return cached.get();
        }

        T value = dbSupplier.get();
        put(key, value, ttl);
        return value;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(json, type));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable while reading key {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {} using simple class deserialization. Falling back to database. Reason: {}",
                    key, e.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(json, type));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable while reading generic key {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis cache read failed for key {} using generic collection deserialization. Falling back to database. Reason: {}",
                    key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable while writing key {}", key);
        } catch (Exception e) {
            log.warn("Failed to write cache key {}. Ignoring cache. Reason: {}", key, e.getMessage(), e);
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete cache key {}", key);
        }
    }
}