package com.team27.amazon.order.service;

import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.repository.ProductRecommendationRepository;
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

import java.math.BigDecimal;
import java.util.Collection;
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
    private ProductRecommendationRepository productRecommendationRepository;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService(
                neo4jClient,
                productRecommendationRepository
        );
    }

    @Test
    void getRecommendations_shouldThrow404_whenSeedProductDoesNotExist() {
        when(productRecommendationRepository.productExists(999L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recommendationService.getRecommendations(999L, 5)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(productRecommendationRepository).productExists(999L);
        verifyNoInteractions(neo4jClient);
    }

    @Test
    void getRecommendations_shouldReturnEmptyList_whenNoGraphRecommendationsExist() {
        when(productRecommendationRepository.productExists(5L)).thenReturn(true);
        mockNeo4jRows(List.of());

        when(productRecommendationRepository.enrichActiveProducts(anyMap()))
                .thenReturn(List.of());

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(5L, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(productRecommendationRepository).productExists(5L);
        verify(productRecommendationRepository).enrichActiveProducts(anyMap());
    }

    @Test
    void getRecommendations_shouldReturnDirectRecommendationsSortedByScore() {
        when(productRecommendationRepository.productExists(1L)).thenReturn(true);

        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productRecommendationRepository.enrichActiveProducts(anyMap()))
                .thenReturn(List.of(
                        new ProductRecommendationDTO(
                                2L,
                                "P2 Headphones",
                                "ELECTRONICS",
                                "Sony",
                                BigDecimal.valueOf(300),
                                3L
                        ),
                        new ProductRecommendationDTO(
                                3L,
                                "P3 Charger",
                                "ELECTRONICS",
                                "Anker",
                                BigDecimal.valueOf(150),
                                1L
                        )
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
        when(productRecommendationRepository.productExists(1L)).thenReturn(true);

        mockNeo4jRows(List.of(
                mapRow(2L, 3L),
                mapRow(3L, 1L)
        ));

        when(productRecommendationRepository.enrichActiveProducts(anyMap()))
                .thenReturn(List.of(
                        new ProductRecommendationDTO(
                                2L,
                                "P2 Headphones",
                                "ELECTRONICS",
                                "Sony",
                                BigDecimal.valueOf(300),
                                3L
                        ),
                        new ProductRecommendationDTO(
                                3L,
                                "P3 Charger",
                                "ELECTRONICS",
                                "Anker",
                                BigDecimal.valueOf(150),
                                1L
                        )
                ));

        List<ProductRecommendationDTO> result =
                recommendationService.getRecommendations(1L, 1);

        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).productId());
        assertEquals(3L, result.get(0).score());
    }

    @Test
    void getRecommendations_shouldExcludeSeedProductFromCandidates() {
        when(productRecommendationRepository.productExists(1L)).thenReturn(true);

        mockNeo4jRows(List.of(
                mapRow(1L, 99L),
                mapRow(2L, 3L)
        ));

        when(productRecommendationRepository.enrichActiveProducts(anyMap()))
                .thenReturn(List.of(
                        new ProductRecommendationDTO(
                                2L,
                                "P2 Headphones",
                                "ELECTRONICS",
                                "Sony",
                                BigDecimal.valueOf(300),
                                3L
                        )
                ));

        recommendationService.getRecommendations(1L, 5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Long, Long>> scoresCaptor =
                ArgumentCaptor.forClass(Map.class);

        verify(productRecommendationRepository).enrichActiveProducts(scoresCaptor.capture());

        Map<Long, Long> scores = scoresCaptor.getValue();

        assertFalse(scores.containsKey(1L));
        assertTrue(scores.containsKey(2L));
        assertEquals(3L, scores.get(2L));
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
    when(productRecommendationRepository.productExists(4L)).thenReturn(true);

    mockNeo4jRows(List.of(
            mapRow(2L, 2L)
    ));

    when(productRecommendationRepository.enrichActiveProducts(anyMap()))
            .thenReturn(List.of(
                    new ProductRecommendationDTO(
                            2L,
                            "P2 Headphones",
                            "ELECTRONICS",
                            "Sony",
                            BigDecimal.valueOf(300),
                            2L
                    )
            ));

    List<ProductRecommendationDTO> result =
            recommendationService.getRecommendations(4L, 5);

    assertEquals(1, result.size());
    assertEquals(2L, result.get(0).productId());
    assertEquals(2L, result.get(0).score());
}

@Test
void getRecommendations_forP1_shouldNotReturnP4BecauseNoDirectEdgeExists() {
    when(productRecommendationRepository.productExists(1L)).thenReturn(true);

    mockNeo4jRows(List.of(
            mapRow(2L, 3L),
            mapRow(3L, 1L)
    ));

    when(productRecommendationRepository.enrichActiveProducts(anyMap()))
            .thenReturn(List.of(
                    new ProductRecommendationDTO(
                            2L,
                            "P2 Headphones",
                            "ELECTRONICS",
                            "Sony",
                            BigDecimal.valueOf(300),
                            3L
                    ),
                    new ProductRecommendationDTO(
                            3L,
                            "P3 Charger",
                            "ELECTRONICS",
                            "Anker",
                            BigDecimal.valueOf(150),
                            1L
                    )
            ));

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
}