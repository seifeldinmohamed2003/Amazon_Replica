package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.product.exception.ProductReviewNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.repository.ProductReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class ProductReviewService {

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private ProductService productService;

    @Transactional
    public ProductReview createReview(Long productId, ProductReviewRequest request) {
        Product product = productService.getProductById(productId);

        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setUserId(request.getUserId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setVerified(false);
        review.setMetadata(new HashMap<>());

        return productReviewRepository.save(review);
    }

    public List<ProductReview> getAllReviews() {
        return productReviewRepository.findAll();
    }

    public ProductReview getReviewById(Long id) {
        return productReviewRepository.findById(id)
                .orElseThrow(() -> new ProductReviewNotFoundException(id));
    }

    public List<ProductReview> getReviewsByProductId(Long productId) {
        return productReviewRepository.findByProductId(productId);
    }

    @Transactional
    public ProductReview updateReview(Long id, ProductReviewRequest request) {
        ProductReview existing = getReviewById(id);
        existing.setUserId(request.getUserId());
        existing.setRating(request.getRating());
        existing.setTitle(request.getTitle());
        existing.setComment(request.getComment());
        return productReviewRepository.save(existing);
    }

    @Transactional
    public void deleteReview(Long id) {
        ProductReview existing = getReviewById(id);
        productReviewRepository.delete(existing);
    }
}
