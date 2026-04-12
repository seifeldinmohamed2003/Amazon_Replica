package com.team27.amazon.product.controller;

import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.product.dto.ProductReviewResponse;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.service.ProductReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ProductReviewController {

    @Autowired
    private ProductReviewService productReviewService;

    @PostMapping
    public ResponseEntity<ProductReviewResponse> createReview(
            @RequestParam Long productId,
            @Valid @RequestBody ProductReviewRequest request
    ) {
        ProductReview created = productReviewService.createReview(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductReviewResponse.from(created));
    }

    @GetMapping
    public List<ProductReviewResponse> getReviews(@RequestParam(required = false) Long productId) {
        List<ProductReview> reviews = productId == null
                ? productReviewService.getAllReviews()
                : productReviewService.getReviewsByProductId(productId);

        return reviews.stream().map(ProductReviewResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProductReviewResponse getReviewById(@PathVariable Long id) {
        return ProductReviewResponse.from(productReviewService.getReviewById(id));
    }

    @PutMapping("/{id}")
    public ProductReviewResponse updateReview(@PathVariable Long id, @Valid @RequestBody ProductReviewRequest request) {
        return ProductReviewResponse.from(productReviewService.updateReview(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        productReviewService.deleteReview(id);
        return ResponseEntity.noContent().build();
    }
}
