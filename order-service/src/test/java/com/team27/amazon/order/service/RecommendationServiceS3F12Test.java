package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ProductExistsDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.order.dto.ProductRecommendationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceS3F12Test {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @Mock
    private ProductServiceClient productServiceClient;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                neo4jClient,
                productServiceClient
        );
    }

    @Test
    void getRecommendations_shouldThrow404_whenSeedProductDoesNotExist() {
        when(productServiceClient.productExists(999L)).thenReturn(new ProductExistsDTO(false));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recommendationService.getRecommendations(999L, 5)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(productServiceClient).productExists(999L);
        verifyNoInteractions(neo4jClient);
    }

    @Test
    void getRecommendations_shouldReturnEmptyList_whenNoGraphRecommendationsExist() {
        when(productServiceClient.productExists(5L)).thenReturn(new ProductExistsDTO(true));
        mockNeo4jRows(List.of());
        when(productServiceClient.getProductsBatch(anyList())).thenReturn(List.of());

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(5L, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productServiceClient).productExists(5L);
        verify(productServiceClient).getProductsBatch(anyList());
    }

    @Test
    void getRecommendations_shouldReturnDirectRecommendationsSortedByScore() {
        when(productServiceClient.productExists(1L)).thenReturn(new ProductExistsDTO(true));

        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(product(2L, "P2 Headphones", "Sony", 300.0), product(3L, "P3 Charger", "Anker", 150.0)));

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
        when(productServiceClient.productExists(1L)).thenReturn(new ProductExistsDTO(true));

        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(product(2L, "P2 Headphones", "Sony", 300.0), product(3L, "P3 Charger", "Anker", 150.0)));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).productId());
        assertEquals(3L, result.get(0).score());
    }

    @Test
    void getRecommendations_shouldExcludeSeedProductFromCandidates() {
        when(productServiceClient.productExists(1L)).thenReturn(new ProductExistsDTO(true));

        mockNeo4jRows(List.of(
                mapRow(1L, 99L),
                mapRow(2L, 3L)
        ));

        when(productServiceClient.getProductsBatch(anyList()))
                .thenReturn(List.of(product(2L, "P2 Headphones", "Sony", 300.0)));

        recommendationService.getRecommendations(1L, 5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(productServiceClient).getProductsBatch(idsCaptor.capture());

        List<Long> ids = idsCaptor.getValue();
        assertFalse(ids.contains(1L));
        assertTrue(ids.contains(2L));
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
    @Test
void getRecommendations_forP4_shouldReturnP2WithScore2() {
    when(productServiceClient.productExists(4L)).thenReturn(new ProductExistsDTO(true));

    mockNeo4jRows(List.of(
            mapRow(2L, 2L)
    ));

    when(productServiceClient.getProductsBatch(anyList()))
            .thenReturn(List.of(product(2L, "P2 Headphones", "Sony", 300.0)));

    List<ProductRecommendationDTO> result =
            recommendationService.getRecommendations(4L, 5);

    assertEquals(1, result.size());
    assertEquals(2L, result.get(0).productId());
    assertEquals(2L, result.get(0).score());
}

@Test
void getRecommendations_forP1_shouldNotReturnP4BecauseNoDirectEdgeExists() {
    when(productServiceClient.productExists(1L)).thenReturn(new ProductExistsDTO(true));

    mockNeo4jRows(List.of(
            mapRow(2L, 3L),
            mapRow(3L, 1L)
    ));

    when(productServiceClient.getProductsBatch(anyList()))
            .thenReturn(List.of(product(2L, "P2 Headphones", "Sony", 300.0), product(3L, "P3 Charger", "Anker", 150.0)));

    List<ProductRecommendationDTO> result =
            recommendationService.getRecommendations(1L, 5);

    assertEquals(2, result.size());

    List<Long> productIds = result.stream()
            .map(ProductRecommendationDTO::productId)
            .toList();

    assertTrue(productIds.contains(2L));
    assertTrue(productIds.contains(3L));
    assertFalse(productIds.contains(4L));
}

    private ProductDTO product(Long id, String name, String brand, Double price) {
        return new ProductDTO(id, name, "Description", price, "ELECTRONICS", brand, 10, "ACTIVE", 0.0, Map.of());
    }
}
