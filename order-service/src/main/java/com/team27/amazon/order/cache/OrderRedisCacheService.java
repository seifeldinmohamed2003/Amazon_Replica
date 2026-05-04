package com.team27.amazon.order.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderRedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(OrderRedisCacheService.class);
    private static final ObjectMapper FALLBACK_OBJECT_MAPPER = new ObjectMapper();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    public <T> Optional<T> get(String key, Class<T> type) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(mapper().readValue(value, type));
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Redis get failed for key {}", key, ex);
            return Optional.empty();
        }
    }

    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        if (stringRedisTemplate == null) {
            return Optional.empty();
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(mapper().readValue(value, typeRef));
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Redis get failed for key {}", key, ex);
            return Optional.empty();
        }
    }

    public void set(String key, Object value, Duration ttl) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            String payload = mapper().writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, payload, ttl);
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Redis set failed for key {}", key, ex);
        }
    }

    public void delete(String key) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ex) {
            log.warn("Redis delete failed for key {}", key, ex);
        }
    }

    @SuppressWarnings("deprecation")
    public void deleteByPattern(String pattern) {
        if (stringRedisTemplate == null) {
            return;
        }
        try {
            stringRedisTemplate.execute((RedisConnection connection) -> {
                List<byte[]> keys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
                try (var cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                } catch (Exception scanEx) {
                    log.warn("Redis scan failed for pattern {}", pattern, scanEx);
                    return null;
                }

                if (keys.isEmpty()) {
                    return null;
                }

                byte[][] keyArray = keys.toArray(byte[][]::new);
                try {
                    connection.unlink(keyArray);
                } catch (Exception unlinkEx) {
                    try {
                        connection.del(keyArray);
                    } catch (Exception delEx) {
                        log.warn("Redis delete failed for pattern {}", pattern, delEx);
                    }
                }
                return null;
            });
        } catch (Exception ex) {
            log.warn("Redis pattern delete failed for pattern {}", pattern, ex);
        }
    }

    public String hash(Object value) {
        try {
            String raw = value instanceof String ? (String) value : mapper().writeValueAsString(value);
            return sha256(raw);
        } catch (RuntimeException | java.io.IOException ex) {
            return sha256(String.valueOf(value));
        }
    }

    public String featureKey(String featureId, Object params) {
        return "order-service::" + featureId + "::" + hash(params);
    }

    public String orderKey(Long orderId) {
        return "order-service::order::" + orderId;
    }

    public String orderItemKey(Long orderItemId) {
        return "order-service::order-item::" + orderItemId;
    }

    public String s3f10DashboardKey(LocalDate startDate, LocalDate endDate) {
        return "order-service::S3-F10::" + startDate + "_" + endDate;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public Map<String, Object> orderedParams(Object... keyValues) {
        java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            params.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return params;
    }

    private ObjectMapper mapper() {
        return objectMapper != null ? objectMapper : FALLBACK_OBJECT_MAPPER;
    }
}
