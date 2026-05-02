package com.team27.amazon.order.repository;

import com.team27.amazon.order.dto.ProductRecommendationDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;

@Repository
public class ProductRecommendationRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean productExists(Long productId) {
        Number count = (Number) entityManager.createNativeQuery("""
            SELECT COUNT(*)
            FROM products
            WHERE id = :productId
        """)
        .setParameter("productId", productId)
        .getSingleResult();

        return count.longValue() > 0;
    }

    public List<ProductRecommendationDTO> enrichActiveProducts(Map<Long, Long> scores) {
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT id, name, category, brand, price
            FROM products
            WHERE id IN (:ids)
              AND status = 'ACTIVE'
        """)
        .setParameter("ids", new ArrayList<>(scores.keySet()))
        .getResultList();

        List<ProductRecommendationDTO> result = new ArrayList<>();

        for (Object[] row : rows) {
            Long productId = ((Number) row[0]).longValue();

            result.add(new ProductRecommendationDTO(
                    productId,
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (BigDecimal) row[4],
                    scores.get(productId)
            ));
        }

        result.sort((a, b) -> Long.compare(b.score(), a.score()));
        return result;
    }
}