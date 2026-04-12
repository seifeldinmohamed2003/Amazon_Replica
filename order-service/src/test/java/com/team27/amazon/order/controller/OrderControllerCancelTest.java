package com.team27.amazon.order.controller;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerCancelTest {

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
    void cancelOrderReturnsUpdatedOrder() throws Exception {
        Order cancelledOrder = new Order();
        cancelledOrder.setId(11L);
        cancelledOrder.setUserId(77L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        cancelledOrder.setMetadata(new HashMap<>());

        when(orderService.cancelOrder(11L)).thenReturn(cancelledOrder);

        mockMvc.perform(put("/api/orders/11/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.userId").value(77));

        verify(orderService).cancelOrder(11L);
    }

    @Test
    void cancelOrderReturnsNotFoundWhenOrderMissing() throws Exception {
        when(orderService.cancelOrder(eq(999L))).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found")
        );

        mockMvc.perform(put("/api/orders/999/cancel"))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrderReturnsBadRequestWhenOrderCannotBeCancelled() throws Exception {
        when(orderService.cancelOrder(eq(11L))).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Only pending or confirmed orders can be cancelled")
        );

        mockMvc.perform(put("/api/orders/11/cancel"))
            .andExpect(status().isBadRequest());
    }
}