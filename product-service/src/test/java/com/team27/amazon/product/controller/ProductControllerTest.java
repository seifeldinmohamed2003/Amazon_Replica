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
import java.util.Map;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void updateSpecificationsMergesIncomingWithExisting() throws Exception {
        Map<String, Object> updatedSpecs = new HashMap<>();
        updatedSpecs.put("screenSize", "6.1in");
        updatedSpecs.put("RAM", "8GB");
        updatedSpecs.put("color", "Silver");
        updatedSpecs.put("storage", "256GB");

        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone");
        product.setDescription("Latest model");
        product.setPrice(999.0);
        product.setCategory("ELECTRONICS");
        product.setBrand("Apple");
        product.setStockQuantity(10);
        product.setStatus(ProductStatus.ACTIVE);
        product.setSpecifications(updatedSpecs);

        when(productService.updateSpecifications(anyLong(), any(Map.class)))
                .thenReturn(product);

        mockMvc.perform(put("/api/products/1/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("color", "Silver", "storage", "256GB"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.screenSize").value("6.1in"))
                .andExpect(jsonPath("$.specifications.RAM").value("8GB"))
                .andExpect(jsonPath("$.specifications.color").value("Silver"))
                .andExpect(jsonPath("$.specifications.storage").value("256GB"));
    }

    @Test
    void updateSpecificationsReturnsNotFoundWhenProductNotFound() throws Exception {
        doThrow(new ProductNotFoundException(99L))
                .when(productService).updateSpecifications(anyLong(), any(Map.class));

        mockMvc.perform(put("/api/products/99/specifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("color", "Black"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }
}
