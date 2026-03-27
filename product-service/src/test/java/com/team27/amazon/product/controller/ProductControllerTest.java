package com.team27.amazon.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.product.dto.ProductRequest;
import com.team27.amazon.product.dto.ProductSalesDTO;
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

import static org.mockito.ArgumentMatchers.eq;

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
            void getProductSalesReturnsSummary() throws Exception {
            ProductSalesDTO dto = new ProductSalesDTO(1L, "Phone", 9L, 899.91, 99.99);

            when(productService.getProductSalesSummary(eq(1L), eq(java.time.LocalDate.parse("2026-03-01")), eq(java.time.LocalDate.parse("2026-03-31"))))
                .thenReturn(dto);

            mockMvc.perform(get("/api/products/1/sales")
                    .param("startDate", "2026-03-01")
                    .param("endDate", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1L))
                .andExpect(jsonPath("$.name").value("Phone"))
                .andExpect(jsonPath("$.totalUnitsSold").value(9))
                .andExpect(jsonPath("$.totalRevenue").value(899.91))
                .andExpect(jsonPath("$.averageSellingPrice").value(99.99));
            }

            @Test
            void getProductSalesReturnsNotFoundWhenMissingProduct() throws Exception {
            doThrow(new ProductNotFoundException(404L)).when(productService)
                .getProductSalesSummary(eq(404L), eq(java.time.LocalDate.parse("2026-03-01")), eq(java.time.LocalDate.parse("2026-03-31")));

            mockMvc.perform(get("/api/products/404/sales")
                    .param("startDate", "2026-03-01")
                    .param("endDate", "2026-03-31"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 404"));
            }
}
