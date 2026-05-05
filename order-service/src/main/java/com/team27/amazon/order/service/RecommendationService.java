package com.team27.amazon.order.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.repository.ProductRecommendationRepository;

@Service
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final ProductRecommendationRepository productRecommendationRepository;
    private final OrderRedisCacheService orderRedisCacheService;

    public RecommendationService(
            Neo4jClient neo4jClient,
            ProductRecommendationRepository productRecommendationRepository,
            OrderRedisCacheService orderRedisCacheService
    ) {
        this.neo4jClient = neo4jClient;
        this.productRecommendationRepository = productRecommendationRepository;
        this.orderRedisCacheService = orderRedisCacheService;
    }

    public List<ProductRecommendationDTO> getRecommendations(Long productId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : limit;

        if (!productRecommendationRepository.productExists(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        String cacheKey = "order-service::S3-F12::productId=" + productId + ":limit=" + safeLimit;

        if (orderRedisCacheService != null) {
            try {
                var cached = orderRedisCacheService.get(
                        cacheKey,
                        new TypeReference<List<ProductRecommendationDTO>>() {
                }
                );
                if (cached.isPresent()) {
                    return cached.get();
                }
            } catch (RuntimeException ignored) {
                // Redis is a soft dependency
            }
        }

        List<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (seed:ProductNode {productId: $productId})-[r:BOUGHT_TOGETHER]-(rec:ProductNode)
                WHERE rec.productId <> $productId
                RETURN rec.productId AS productId, r.coPurchaseCount AS score
                ORDER BY score DESC
                LIMIT $limit
            """)
                .bind(productId).to("productId")
                .bind(safeLimit).to("limit")
                .fetch()
                .all()
                .stream()
                .toList();

        Map<Long, Long> scores = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            Object productIdValue = row.get("productId");
            Object scoreValue = row.get("score");

            if (productIdValue == null || scoreValue == null) {
                continue;
            }

            Long recommendedProductId = ((Number) productIdValue).longValue();
            Long score = ((Number) scoreValue).longValue();

            if (!recommendedProductId.equals(productId)) {
                scores.put(recommendedProductId, score);
            }
        }

        List<ProductRecommendationDTO> result = productRecommendationRepository.enrichActiveProducts(scores)
                .stream()
                .limit(safeLimit)
                .toList();

        if (orderRedisCacheService != null) {
            try {
                orderRedisCacheService.set(cacheKey, result, Duration.ofMinutes(5));
            } catch (RuntimeException ignored) {
                // Redis is a soft dependency
            }
        }

        return result;
    }
}
