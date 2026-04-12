package com.team27.amazon.product.repository;

import com.team27.amazon.product.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    Optional<ProductReview> findByIdAndProductId(Long id, Long productId);

    List<ProductReview> findByProductId(Long productId);
}