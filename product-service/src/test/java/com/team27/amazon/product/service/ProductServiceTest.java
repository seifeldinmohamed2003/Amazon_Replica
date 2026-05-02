package com.team27.amazon.product.service;

import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductSalesDTO;
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
import com.team27.amazon.product.cache.RedisCacheService;
import com.team27.amazon.product.cache.ProductCacheInvalidator;
import com.team27.amazon.product.repository.ProductReviewRepository;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.product.adapter.ObjectArrayDtoAdapter;
import java.util.function.Supplier;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private ProductCacheInvalidator productCacheInvalidator;

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private MongoEventLogger mongoEventLogger;

    @Mock
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;

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

        // Make redisCacheService return supplier result by default to avoid caching NPEs
        lenient().when(redisCacheService.getOrLoad(anyString(), any(), any(), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });

        // Default adapter behavior for product sales DTO construction
        lenient().when(objectArrayDtoAdapter.toProductSalesDTO(any(Long.class), any(String.class), any()))
            .thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                String name = invocation.getArgument(1);
                Object[] arr = invocation.getArgument(2);
                Long units = arr == null || arr[0] == null ? 0L : ((Number) arr[0]).longValue();
                Double rev = arr == null || arr[1] == null ? 0.0 : ((Number) arr[1]).doubleValue();
                Double avg = units == 0L ? 0.0 : rev / units;
                return ProductSalesDTO.builder()
                    .productId(id)
                    .name(name)
                    .totalUnitsSold(units)
                    .totalRevenue(rev)
                    .averageSellingPrice(avg)
                    .build();
            });
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

    @Test
    void getProductSalesSummaryReturnsAggregatedValues() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Phone");

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.getProductSalesSummary(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new Object[]{9L, 899.91});

        ProductSalesDTO result = productService.getProductSalesSummary(
                1L,
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-31")
        );

        assertEquals(1L, result.getProductId());
        assertEquals("Phone", result.getName());
        assertEquals(9L, result.getTotalUnitsSold());
        assertEquals(899.91, result.getTotalRevenue());
        assertEquals(99.99, result.getAverageSellingPrice(), 0.000001);
    }

    @Test
    void getProductSalesSummaryReturnsZeroesWhenNoOrders() {
        Product product = new Product();
        product.setId(3L);
        product.setName("Tablet");

        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productRepository.getProductSalesSummary(eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(new Object[]{0L, 0.0});

        ProductSalesDTO result = productService.getProductSalesSummary(
                3L,
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30")
        );

        assertEquals(0L, result.getTotalUnitsSold());
        assertEquals(0.0, result.getTotalRevenue());
        assertEquals(0.0, result.getAverageSellingPrice());
    }

    @Test
    void getProductSalesSummaryThrowsWhenProductMissing() {
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getProductSalesSummary(
                404L,
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-31")
        ));
    }

    @Test
    void updateSpecificationsAccumulatesAcrossSequentialUpdates() {
        Product existing = new Product();
        existing.setId(5L);
        existing.setSpecifications(new java.util.HashMap<>());

        when(productRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateSpecifications(5L, Map.of("screenSize", "6.1in"));
        productService.updateSpecifications(5L, Map.of("RAM", "8GB"));
        productService.updateSpecifications(5L, Map.of("color", "Black"));

        Map<String, Object> result = existing.getSpecifications();
        assertEquals("6.1in", result.get("screenSize"));
        assertEquals("8GB", result.get("RAM"));
        assertEquals("Black", result.get("color"));
    }
}

