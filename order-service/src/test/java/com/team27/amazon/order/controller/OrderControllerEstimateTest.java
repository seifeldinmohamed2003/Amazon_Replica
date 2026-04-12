package com.team27.amazon.order.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerEstimateTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController orderController = new OrderController(orderService);
        ReflectionTestUtils.setField(orderController, "orderService", orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void estimateOrderReturnsCalculatedDto() throws Exception {
        OrderEstimateDTO dto = new OrderEstimateDTO(3, 400.0, 50.0, 450.0, 0.0);

        when(orderService.estimateOrderPrice(anyList())).thenReturn(dto);

        mockMvc.perform(post("/api/orders/estimate")
                .contentType("application/json")
                .content("[{\"productId\":1,\"quantity\":2},{\"productId\":2,\"quantity\":1}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itemCount").value(3))
            .andExpect(jsonPath("$.subtotal").value(400.0))
            .andExpect(jsonPath("$.shippingCost").value(50.0))
            .andExpect(jsonPath("$.estimatedTotal").value(450.0))
            .andExpect(jsonPath("$.discountApplied").value(0.0));

            verify(orderService).estimateOrderPrice(anyList());
    }

    @Test
    void estimateOrderReturnsNotFoundWhenProductMissing() throws Exception {
            when(orderService.estimateOrderPrice(anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        mockMvc.perform(post("/api/orders/estimate")
                .contentType("application/json")
                .content("[{\"productId\":999,\"quantity\":1}]"))
            .andExpect(status().isNotFound());
    }

    @Test
    void estimateOrderReturnsBadRequestWhenQuantityMissing() throws Exception {
        when(orderService.estimateOrderPrice(anyList()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each item must include productId and quantity >= 1"));

        mockMvc.perform(post("/api/orders/estimate")
                .contentType("application/json")
                .content("[{\"productId\":1}]"))
            .andExpect(status().isBadRequest());
    }
}
