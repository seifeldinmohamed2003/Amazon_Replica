package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductSalesDTO;
import com.team27.amazon.product.exception.ProductNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

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

    public ProductSalesDTO getProductSalesSummary(Long productId, LocalDate startDate, LocalDate endDate) {
        Product product = getProductById(productId);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        Object[] result = productRepository.getProductSalesSummary(productId, startDateTime, endDateTime);

        long totalUnitsSold = 0L;
        double totalRevenue = 0.0;

        if (result != null && result.length >= 2) {
            totalUnitsSold = result[0] == null ? 0L : ((Number) result[0]).longValue();
            totalRevenue = result[1] == null ? 0.0 : ((Number) result[1]).doubleValue();
        }

        double averageSellingPrice = totalUnitsSold == 0 ? 0.0 : totalRevenue / totalUnitsSold;

        return new ProductSalesDTO(
                product.getId(),
                product.getName(),
                totalUnitsSold,
                totalRevenue,
                averageSellingPrice
        );
    }

    public void deleteProduct(Long id) {
        Product existing = getProductById(id);
        productRepository.delete(existing);
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
