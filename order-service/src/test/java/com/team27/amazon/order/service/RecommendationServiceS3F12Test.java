package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.repository.ProductRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceS3F12Test {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @Mock
    private ProductRecommendationRepository productRecommendationRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                neo4jClient,
                productRecommendationRepository,
                productServiceClient
        );
    }

    @Test
    void getRecommendations_shouldThrow404_whenSeedProductDoesNotExist() {
        when(productServiceClient.getProduct(999L)).thenThrow(new RuntimeException("Not found"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recommendationService.getRecommendations(999L, 5)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(productServiceClient).getProduct(999L);
        verifyNoInteractions(neo4jClient);
    }

    @Test
    void getRecommendations_shouldReturnEmptyList_whenNoGraphRecommendationsExist() {
        mockProductServiceClient(999L, "P999", "ELECTRONICS", "Brand", 100.0, "ACTIVE");
        mockNeo4jRows(List.of());

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(999L, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productServiceClient).getProduct(999L);
    }

    @Test
    void getRecommendations_shouldReturnDirectRecommendationsSortedByScore() {
        mockProductServiceClient(1L, "P1 Laptop", "ELECTRONICS", "Dell", 1000.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE"),
                        mockProduct(3L, "P3 Charger", "ELECTRONICS", "Anker", 150.0, "ACTIVE")
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 5);

        assertEquals(2, result.size());

        assertEquals(2L, result.get(0).productId());
        assertEquals(3L, result.get(0).score());

        assertEquals(3L, result.get(1).productId());
        assertEquals(1L, result.get(1).score());
    }

    @Test
    void getRecommendations_shouldRespectLimitAfterEnrichment() {
        mockProductServiceClient(1L, "P1 Laptop", "ELECTRONICS", "Dell", 1000.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE"),
                        mockProduct(3L, "P3 Charger", "ELECTRONICS", "Anker", 150.0, "ACTIVE")
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).productId());
        assertEquals(3L, result.get(0).score());
    }

    @Test
    void getRecommendations_shouldExcludeSeedProductFromCandidates() {
        mockProductServiceClient(1L, "P1 Laptop", "ELECTRONICS", "Dell", 1000.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(1L, 99L),
                mapRow(2L, 3L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE")
                ));

        recommendationService.getRecommendations(1L, 5);

        // Verify that seed product (1L) was not passed to getProductsBatch
        verify(productServiceClient).getProductsBatch(argThat(list -> 
            !list.contains(1L) && list.contains(2L)
        ));
    }

    @Test
    void getRecommendations_forP4_shouldReturnP2WithScore2() {
        mockProductServiceClient(4L, "P4 Phone", "ELECTRONICS", "Apple", 1500.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(2L, 2L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE")
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(4L, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).productId());
        assertEquals(2L, result.get(0).score());
    }

    @Test
    void getRecommendations_forP1_shouldNotReturnP4BecauseNoDirectEdgeExists() {
        mockProductServiceClient(1L, "P1 Laptop", "ELECTRONICS", "Dell", 1000.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE"),
                        mockProduct(3L, "P3 Charger", "ELECTRONICS", "Anker", 150.0, "ACTIVE")
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 5);

        assertEquals(2, result.size());
        for (ProductRecommendationDTO dto : result) {
            assertNotEquals(4L, dto.productId());
        }
    }

    @Test
    void getRecommendations_shouldFilterOutInactiveProducts() {
        mockProductServiceClient(1L, "P1 Laptop", "ELECTRONICS", "Dell", 1000.0, "ACTIVE");
        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(5L, 2L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(
                        mockProduct(2L, "P2 Headphones", "ELECTRONICS", "Sony", 300.0, "ACTIVE"),
                        mockProduct(5L, "P5 Cable", "ELECTRONICS", "Generic", 20.0, "INACTIVE")
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 5);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).productId());
    }

    private void mockProductServiceClient(Long id, String name, String category, String brand, Double price, String status) {
        ProductDTO mockDto = mockProduct(id, name, category, brand, price, status);
        when(productServiceClient.getProduct(id)).thenReturn(mockDto);
    }

    private ProductDTO mockProduct(Long id, String name, String category, String brand, Double price, String status) {
        return new ProductDTO(
                id,
                name,
                "Product description",
                price,
                category,
                brand,
                100,
                status,
                4.5,
                new HashMap<>()
        );
    }

    private void mockNeo4jRows(Collection<Map<String, Object>> rows) {
        when(neo4jClient.query(anyString())
                .bind(any())
                .to(anyString())
                .bind(any())
                .to(anyString())
                .fetch()
                .all()
        ).thenReturn(rows);
    }

    private Map<String, Object> mapRow(Long productId, Long score) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", productId);
        row.put("score", score);
        return row;
    }
}
