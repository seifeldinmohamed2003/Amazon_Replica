package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.exception.ProductNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private ProductRequest request;

    @BeforeEach
    void setUp() {
        request = new ProductRequest();
        request.setName("Laptop");
        request.setDescription("Powerful laptop");
        request.setPrice(1200.0);
        request.setCategory("Electronics");
        request.setBrand("BrandX");
        request.setStockQuantity(20);
        request.setStatus(ProductStatus.ACTIVE);
        request.setSpecifications(Map.of("ram", "16GB"));
    }

    @Test
    void createProductSavesMappedEntity() {
        Product saved = new Product();
        saved.setId(1L);
        saved.setName("Laptop");

        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = productService.createProduct(request);

        assertEquals(1L, result.getId());
        assertEquals("Laptop", result.getName());
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void getProductsUsesStatusFilterWhenProvided() {
        when(productRepository.findByStatus(ProductStatus.ACTIVE)).thenReturn(List.of(new Product()));

        List<Product> result = productService.getProducts(ProductStatus.ACTIVE, null);

        assertEquals(1, result.size());
        verify(productRepository).findByStatus(ProductStatus.ACTIVE);
        verify(productRepository, never()).findAll();
    }

    @Test
    void updateProductThrowsWhenMissing() {
        when(productRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.updateProduct(10L, request));
    }

    @Test
    void deleteProductDeletesWhenFound() {
        Product existing = new Product();
        existing.setId(2L);
        when(productRepository.findById(2L)).thenReturn(Optional.of(existing));

        productService.deleteProduct(2L);

        verify(productRepository).delete(existing);
    }
}

