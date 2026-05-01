package com.team27.amazon.shipping.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
@SuppressWarnings({"deprecation", "removal"})
public class RedisConfiguration {

    public static final String SERVICE_PREFIX = "shipping-service";

    public static final String SHIPMENT_DETAIL_KEY_PREFIX = SERVICE_PREFIX + "::shipment::";

    public static final String S4_F1_KEY_PREFIX = SERVICE_PREFIX + "::S4-F1::";
    public static final String S4_F3_KEY_PREFIX = SERVICE_PREFIX + "::S4-F3::";
    public static final String S4_F5_KEY_PREFIX = SERVICE_PREFIX + "::S4-F5::";
    public static final String S4_F6_KEY_PREFIX = SERVICE_PREFIX + "::S4-F6::";
    public static final String S4_F8_KEY_PREFIX = SERVICE_PREFIX + "::S4-F8::";
    public static final String S4_F9_KEY_PREFIX = SERVICE_PREFIX + "::S4-F9::";

    public static final String CACHE_SHIPMENT_DETAIL = "shipping-service::shipment";
    public static final String CACHE_S4_F1 = "shipping-service::S4-F1";
    public static final String CACHE_S4_F3 = "shipping-service::S4-F3";
    public static final String CACHE_S4_F5 = "shipping-service::S4-F5";
    public static final String CACHE_S4_F6 = "shipping-service::S4-F6";
    public static final String CACHE_S4_F8 = "shipping-service::S4-F8";
    public static final String CACHE_S4_F9 = "shipping-service::S4-F9";

    private static final Duration GET_BY_ID_TTL = Duration.ofMinutes(15);
    private static final Duration F1_TTL = Duration.ofMinutes(5);
    private static final Duration F3_TTL = Duration.ofMinutes(10);
    private static final Duration F5_TTL = Duration.ofMinutes(5);
    private static final Duration F6_TTL = Duration.ofMinutes(10);
    private static final Duration F8_TTL = Duration.ofMinutes(15);
    private static final Duration F9_TTL = Duration.ofMinutes(10);

    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(objectMapper, null);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(redisJsonSerializer)
                )
                .disableCachingNullValues();

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
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer redisJsonSerializer
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(redisJsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(redisJsonSerializer);
        template.afterPropertiesSet();
        return template;
    }
}