package com.team27.amazon.order.service;

import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.repository.ProductRecommendationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final ProductRecommendationRepository productRecommendationRepository;

    public RecommendationService(
            Neo4jClient neo4jClient,
            ProductRecommendationRepository productRecommendationRepository
    ) {
        this.neo4jClient = neo4jClient;
        this.productRecommendationRepository = productRecommendationRepository;
    }

    @Cacheable(
            value = "order:recommendations",
            key = "'product:' + #productId + ':limit:' + (#limit == null ? 5 : #limit)"
    )
    public List<ProductRecommendationDTO> getRecommendations(Long productId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : limit;

        if (!productRecommendationRepository.productExists(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
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

        return productRecommendationRepository.enrichActiveProducts(scores)
                .stream()
                .limit(safeLimit)
                .toList();
    }
}