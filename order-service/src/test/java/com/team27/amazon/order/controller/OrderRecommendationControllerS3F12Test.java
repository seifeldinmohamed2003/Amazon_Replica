package com.team27.amazon.order.controller;

import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.service.RecommendationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(MockitoExtension.class)
class OrderRecommendationControllerS3F12Test {

    @Mock
    private RecommendationService recommendationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderRecommendationController controller =
                new OrderRecommendationController(recommendationService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void getRecommendations_shouldReturnRecommendationsWithStatus200() throws Exception {
        when(recommendationService.getRecommendations(1L, 5))
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

        mockMvc.perform(get("/api/orders/recommendations")
                        .param("productId", "1")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productId").value(2))
                .andExpect(jsonPath("$[0].score").value(3))
                .andExpect(jsonPath("$[1].productId").value(3))
                .andExpect(jsonPath("$[1].score").value(1));

        verify(recommendationService).getRecommendations(1L, 5);
    }

    @Test
    void getRecommendations_shouldUseDefaultLimit5_whenLimitNotProvided() throws Exception {
        when(recommendationService.getRecommendations(1L, 5))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/orders/recommendations")
                        .param("productId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(recommendationService).getRecommendations(1L, 5);
    }

    @Test
    void getRecommendations_shouldRespectLimit1() throws Exception {
        when(recommendationService.getRecommendations(1L, 1))
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

        mockMvc.perform(get("/api/orders/recommendations")
                        .param("productId", "1")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].productId").value(2))
                .andExpect(jsonPath("$[0].score").value(3));

        verify(recommendationService).getRecommendations(1L, 1);
    }

    @Test
    void getRecommendations_shouldReturnEmptyList_whenNoRecommendationsExist() throws Exception {
        when(recommendationService.getRecommendations(5L, 5))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/orders/recommendations")
                        .param("productId", "5")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(recommendationService).getRecommendations(5L, 5);
    }

    @Test
    void getRecommendations_shouldReturn404_whenProductNotFound() throws Exception {
        when(recommendationService.getRecommendations(999L, 5))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                ));

        mockMvc.perform(get("/api/orders/recommendations")
                        .param("productId", "999")
                        .param("limit", "5"))
                .andExpect(status().isNotFound());

        verify(recommendationService).getRecommendations(999L, 5);
    }
}