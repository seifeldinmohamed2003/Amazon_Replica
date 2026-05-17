package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.repository.ProductRecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final Neo4jClient neo4jClient;
    private final ProductRecommendationRepository productRecommendationRepository;
    private final ProductServiceClient productServiceClient;

    public RecommendationService(
            Neo4jClient neo4jClient,
            ProductRecommendationRepository productRecommendationRepository,
            ProductServiceClient productServiceClient
    ) {
        this.neo4jClient = neo4jClient;
        this.productRecommendationRepository = productRecommendationRepository;
        this.productServiceClient = productServiceClient;
    }

    @Cacheable(
            value = "order:recommendations",
            key = "'product:' + #productId + ':limit:' + (#limit == null ? 5 : #limit)"
    )
    public List<ProductRecommendationDTO> getRecommendations(Long productId, Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 5 : limit;

        // S3-F12: Use ProductServiceClient to validate seed product exists (Feign read)
        try {
            ProductDTO seedProduct = productServiceClient.getProduct(productId);
            if (seedProduct == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            log.info("Validated seed product {} for recommendations", productId);
        } catch (Exception e) {
            log.error("Failed to validate seed product: {}", e.getMessage());
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

        // S3-F12: Use ProductServiceClient batch to enrich and filter by status (Feign read)
        return enrichActiveProductsWithFeign(scores, safeLimit);
    }

    /**
     * S3-F12: Enrich recommendation scores with product data from ProductServiceClient
     * and filter only ACTIVE products.
     */
    private List<ProductRecommendationDTO> enrichActiveProductsWithFeign(Map<Long, Long> scores, int limit) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }

        List<ProductDTO> products = productServiceClient.getProductsBatch(
                List.copyOf(scores.keySet())
        );

        List<ProductRecommendationDTO> result = new java.util.ArrayList<>();

        for (ProductDTO product : products) {
            // Filter only ACTIVE products
            if (product.status() != null && "ACTIVE".equalsIgnoreCase(product.status())) {
                Long score = scores.get(product.id());
                result.add(new ProductRecommendationDTO(
                        product.id(),
                        product.name(),
                        product.category(),
                        product.brand(),
                        product.price() != null ? java.math.BigDecimal.valueOf(product.price()) : null,
                        score
                ));
                if (result.size() >= limit) {
                    break;
                }
            }
        }

        log.info("Enriched {} products from batch Feign, filtered to {} active products", 
                 products.size(), result.size());
        return result;
    }
}