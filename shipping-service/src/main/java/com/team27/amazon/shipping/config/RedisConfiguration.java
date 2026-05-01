package com.team27.amazon.shipping.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for Shipping Service.
 * 
 * Cache Key Convention (per spec 4.4.5):
 * - Entity detail: shipping-service::shipment::{id}
 * - Feature result: shipping-service::S4-F{n}::{param-hash}
 * 
 * TTLs (per spec 4.4.1):
 * - F1 = 5 min (search)
 * - F3 = 10 min (DTO)
 * - F5 = 5 min (JSONB query)
 * - F6 = 10 min (report)
 * - F8 = 15 min (relationship DTO)
 * - F9 = 10 min (combined)
 * - GET by ID = 15 min (CRUD baseline)
 */
@Configuration
@EnableCaching
public class RedisConfiguration {

    // Service prefix for cache keys
    public static final String SERVICE_PREFIX = "shipping-service";
    
    // Entity detail cache key pattern: shipping-service::shipment::{id}
    public static final String SHIPMENT_DETAIL_KEY_PREFIX = SERVICE_PREFIX + "::shipment::";
    
    // Feature result cache key patterns: shipping-service::S4-F{n}::
    public static final String S4_F1_KEY_PREFIX = SERVICE_PREFIX + "::S4-F1::";   // Get Latest Shipment for Order
    public static final String S4_F3_KEY_PREFIX = SERVICE_PREFIX + "::S4-F3::";   // Find Nearby Shipments
    public static final String S4_F5_KEY_PREFIX = SERVICE_PREFIX + "::S4-F5::";   // Filter by Metadata (JSONB)
    public static final String S4_F6_KEY_PREFIX = SERVICE_PREFIX + "::S4-F6::";   // Date Range
    public static final String S4_F8_KEY_PREFIX = SERVICE_PREFIX + "::S4-F8::";   // Carrier Performance Summary
    public static final String S4_F9_KEY_PREFIX = SERVICE_PREFIX + "::S4-F9::";   // Delayed Shipments

    // Cache names (used by Spring Cache abstraction)
    public static final String CACHE_SHIPMENT_DETAIL = "shipping-service::shipment";       // GET by ID - 15 min
    public static final String CACHE_S4_F1 = "shipping-service::S4-F1";                    // F1 - 5 min
    public static final String CACHE_S4_F3 = "shipping-service::S4-F3";                    // F3 - 10 min
    public static final String CACHE_S4_F5 = "shipping-service::S4-F5";                    // F5 - 5 min
    public static final String CACHE_S4_F6 = "shipping-service::S4-F6";                    // F6 - 10 min
    public static final String CACHE_S4_F8 = "shipping-service::S4-F8";                    // F8 - 15 min
    public static final String CACHE_S4_F9 = "shipping-service::S4-F9";                    // F9 - 10 min

    // TTLs by feature type (as per specification)
    private static final Duration GET_BY_ID_TTL = Duration.ofMinutes(15); // CRUD baseline
    private static final Duration F1_TTL = Duration.ofMinutes(5);   // search
    private static final Duration F3_TTL = Duration.ofMinutes(10);  // DTO
    private static final Duration F5_TTL = Duration.ofMinutes(5);   // JSONB query
    private static final Duration F6_TTL = Duration.ofMinutes(10);  // report
    private static final Duration F8_TTL = Duration.ofMinutes(15);  // relationship DTO
    private static final Duration F9_TTL = Duration.ofMinutes(10);  // combined

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Configure specific TTLs for each cache
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put(CACHE_SHIPMENT_DETAIL, defaultConfig.entryTtl(GET_BY_ID_TTL));
        cacheConfigurations.put(CACHE_S4_F1, defaultConfig.entryTtl(F1_TTL));
        cacheConfigurations.put(CACHE_S4_F3, defaultConfig.entryTtl(F3_TTL));
        cacheConfigurations.put(CACHE_S4_F5, defaultConfig.entryTtl(F5_TTL));
        cacheConfigurations.put(CACHE_S4_F6, defaultConfig.entryTtl(F6_TTL));
        cacheConfigurations.put(CACHE_S4_F8, defaultConfig.entryTtl(F8_TTL));
        cacheConfigurations.put(CACHE_S4_F9, defaultConfig.entryTtl(F9_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}