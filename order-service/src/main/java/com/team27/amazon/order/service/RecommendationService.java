package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.order.dto.ProductRecommendationDTO;
import feign.FeignException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final ProductServiceClient productServiceClient;

    public RecommendationService(
            Neo4jClient neo4jClient,
            ProductServiceClient productServiceClient
    ) {
        this.neo4jClient = neo4jClient;
        this.productServiceClient = productServiceClient;
    }

    @Cacheable(
            value = "order:recommendations",
            key = "'product:' + #productId + ':limit:' + (#limit == null ? 5 : #limit)"
    )
    public List<ProductRecommendationDTO> getRecommendations(Long productId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : limit;

        verifySeedProductExists(productId);

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

        return enrichActiveProducts(scores)
                .stream()
                .limit(safeLimit)
                .toList();
    }

    private void verifySeedProductExists(Long productId) {
        try {
            if (!productServiceClient.productExists(productId).exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found", ex);
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service temporarily unavailable", ex);
        }
    }

    private List<ProductRecommendationDTO> enrichActiveProducts(Map<Long, Long> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }

        List<ProductDTO> products;
        try {
            products = productServiceClient.getProductsBatch(new ArrayList<>(scores.keySet()));
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service temporarily unavailable", ex);
        }

        return products.stream()
                .filter(product -> "ACTIVE".equalsIgnoreCase(product.status()))
                .filter(product -> scores.containsKey(product.id()))
                .map(product -> new ProductRecommendationDTO(
                        product.id(),
                        product.name(),
                        product.category(),
                        product.brand(),
                        BigDecimal.valueOf(product.price() == null ? 0.0 : product.price()),
                        scores.get(product.id())
                ))
                .sorted(Comparator.comparing(ProductRecommendationDTO::score).reversed())
                .toList();
    }
}
