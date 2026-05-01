package com.team27.amazon.user.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(
                Jackson2ObjectMapperBuilder.json()
                        .modules(new JavaTimeModule())
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()
        );

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .entryTtl(Duration.ofMinutes(5));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // dashboards / analytics -> 10 minutes (S1-F6)
        cacheConfigs.put("user-service::S1-F6", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // search results -> 5 minutes (S1-F1)
        cacheConfigs.put("user-service::S1-F1", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // activity feeds -> 5 minutes
        cacheConfigs.put("user-service::activity", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // entity detail views -> 15 minutes (entity detail caches)
        cacheConfigs.put("user-service::user", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put("user-service::S1-F8", defaultConfig.entryTtl(Duration.ofMinutes(15))); // profile
        cacheConfigs.put("user-service::S1-F3", defaultConfig.entryTtl(Duration.ofMinutes(15))); // order-summary
        cacheConfigs.put("user-service::shipping-address", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        // S1-F5 (preference search) -> 5 minutes
        cacheConfigs.put("user-service::S1-F5", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}