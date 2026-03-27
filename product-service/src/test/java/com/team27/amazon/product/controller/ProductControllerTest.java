package com.team27.amazon.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.exception.GlobalExceptionHandler;
import com.team27.amazon.product.exception.ProductNotFoundException;
import com.team27.amazon.product.model.Product;
import com.team27.amazon.product.model.ProductStatus;
import com.team27.amazon.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createProductReturnsCreated() throws Exception {
        Product saved = new Product();
        saved.setId(1L);
        saved.setName("Phone");
        saved.setDescription("Smart phone");
        saved.setPrice(699.0);
        saved.setCategory("Electronics");
        saved.setBrand("BrandY");
        saved.setStockQuantity(10);
        saved.setStatus(ProductStatus.ACTIVE);

        when(productService.createProduct(any(ProductRequest.class))).thenReturn(saved);

        ProductRequest request = new ProductRequest();
        request.setName("Phone");
        request.setDescription("Smart phone");
        request.setPrice(699.0);
        request.setCategory("Electronics");
        request.setBrand("BrandY");
        request.setStockQuantity(10);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Phone"));
    }

    @Test
    void getProductsReturnsList() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setName("Book");
        product.setDescription("Novel");
        product.setPrice(20.0);
        product.setCategory("Books");
        product.setBrand("Publisher");
        product.setStockQuantity(50);
        product.setStatus(ProductStatus.ACTIVE);

        when(productService.getProducts(null, null)).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Book"));
    }

    @Test
    void deleteProductReturnsNotFoundWhenMissing() throws Exception {
        doThrow(new ProductNotFoundException(99L)).when(productService).deleteProduct(99L);

        mockMvc.perform(delete("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void searchProductsByCategoryAndPriceRange() throws Exception {
        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("Laptop");
        product1.setDescription("High-end laptop");
        product1.setPrice(99.99);
        product1.setCategory("ELECTRONICS");
        product1.setBrand("BrandX");
        product1.setStockQuantity(5);
        product1.setStatus(ProductStatus.ACTIVE);

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("Monitor");
        product2.setDescription("4K Monitor");
        product2.setPrice(149.99);
        product2.setCategory("ELECTRONICS");
        product2.setBrand("BrandY");
        product2.setStockQuantity(8);
        product2.setStatus(ProductStatus.ACTIVE);

        when(productService.searchProducts(50.0, 200.0, "ELECTRONICS"))
                .thenReturn(List.of(product1, product2));

        mockMvc.perform(get("/api/products/search")
                        .param("category", "ELECTRONICS")
                        .param("minPrice", "50")
                        .param("maxPrice", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(99.99))
                .andExpect(jsonPath("$[1].price").value(149.99));
    }

    @Test
    void searchProductsByPriceRangeNoCategoryFilter() throws Exception {
        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("Shirt");
        product1.setDescription("Cotton shirt");
        product1.setPrice(25.0);
        product1.setCategory("CLOTHING");
        product1.setBrand("BrandZ");
        product1.setStockQuantity(20);
        product1.setStatus(ProductStatus.ACTIVE);

        when(productService.searchProducts(20.0, 30.0, null))
                .thenReturn(List.of(product1));

        mockMvc.perform(get("/api/products/search")
                        .param("minPrice", "20")
                        .param("maxPrice", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(25.0));
    }

    @Test
    void searchProductsInvalidPriceRangeThrowsBadRequest() throws Exception {
        doThrow(new com.team27.amazon.product.exception.InvalidPriceRangeException(200.0, 50.0))
                .when(productService).searchProducts(200.0, 50.0, null);

        mockMvc.perform(get("/api/products/search")
                        .param("minPrice", "200")
                        .param("maxPrice", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
