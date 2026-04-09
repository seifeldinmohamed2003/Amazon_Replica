package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductReviewRequest;
import com.team27.amazon.product.exception.InvalidReviewException;
import com.team27.amazon.product.exception.ProductNotFoundException;
import com.team27.amazon.product.exception.UserNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductReview;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.repository.ProductRepository;
import com.team27.amazon.product.repository.ProductReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public List<Product> getProducts(ProductStatus status, String category) {
        if (status != null) {
            return productRepository.findByStatus(status);
        }
        if (category != null && !category.isBlank()) {
            return productRepository.findByCategoryIgnoreCase(category);
        }
        return productRepository.findAll();
    }

    public Product updateProduct(Long id, ProductRequest request) {
        Product existing = getProductById(id);
        applyRequest(existing, request);
        return productRepository.save(existing);
    }

    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
    }

    @Transactional
    public ProductReview addReview(Long productId, ProductReviewRequest request) {
        Product product = getProductById(productId);

        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewException("Rating must be between 1 and 5.");
        }

        if (!productRepository.userExists(request.getUserId())) {
            throw new UserNotFoundException(request.getUserId());
        }

        if (!productRepository.hasDeliveredPurchase(request.getUserId(), productId)) {
            throw new InvalidReviewException("User must purchase this product before reviewing it.");
        }

        ProductReview review = new ProductReview();
        review.setUserId(request.getUserId());
        review.setRating(request.getRating());
        review.setTitle(request.getTitle());
        review.setComment(request.getComment());
        review.setVerified(false);
        review.setMetadata(new HashMap<>());

        product.addReview(review);
        productReviewRepository.save(review);

        int oldCount = product.getTotalRatings() == null ? 0 : product.getTotalRatings();
        double oldAverage = product.getRating() == null ? 0.0 : product.getRating();

        int newCount = oldCount + 1;
        double newAverage = ((oldAverage * oldCount) + request.getRating()) / newCount;

        product.setTotalRatings(newCount);
        product.setRating(newAverage);

        productRepository.save(product);

        return review;
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setStockQuantity(request.getStockQuantity());
        product.setStatus(request.getStatus() == null ? ProductStatus.ACTIVE : request.getStatus());

        Map<String, Object> specifications = request.getSpecifications();
        product.setSpecifications(specifications == null ? new HashMap<>() : new HashMap<>(specifications));
    }
}