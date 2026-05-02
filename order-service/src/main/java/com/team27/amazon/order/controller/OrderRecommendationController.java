package com.team27.amazon.order.controller;

import com.team27.amazon.order.dto.ProductRecommendationDTO;
import com.team27.amazon.order.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderRecommendationController {

    private final RecommendationService recommendationService;

    public OrderRecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<ProductRecommendationDTO>> getRecommendations(
            @RequestParam Long productId,
            @RequestParam(required = false, defaultValue = "5") Integer limit
    ) {
        return ResponseEntity.ok(
                recommendationService.getRecommendations(productId, limit)
        );
    }
}