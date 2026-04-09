package com.team27.amazon.product.controller;

import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductResponse;
import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.product.dto.ProductReviewResponse;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @GetMapping
    public List<ProductResponse> getProducts(
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String category
    ) {
        return productService.getProducts(status, category).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return ProductResponse.from(productService.getProductById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reviews")
    public ResponseEntity<ProductReviewResponse> addReview(
            @PathVariable Long id,
            @Valid @RequestBody ProductReviewRequest request
    ) {
        ProductReview review = productService.addReview(id, request);
        return ResponseEntity.ok(ProductReviewResponse.from(review));
    }
}