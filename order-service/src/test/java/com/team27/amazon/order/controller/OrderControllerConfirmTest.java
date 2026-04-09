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
class OrderControllerConfirmTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController orderController = new OrderController();
        ReflectionTestUtils.setField(orderController, "orderService", orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void confirmOrderReturnsUpdatedOrder() throws Exception {
        Order confirmedOrder = new Order();
        confirmedOrder.setId(11L);
        confirmedOrder.setUserId(77L);
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);
        confirmedOrder.setShippingAddressId(99L);
        confirmedOrder.setTotalAmount(32.5);
        confirmedOrder.setMetadata(new HashMap<>());

        when(orderService.confirmOrder(11L, 99L)).thenReturn(confirmedOrder);

        mockMvc.perform(put("/api/orders/11/confirm")
                .param("shippingAddressId", "99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.shippingAddressId").value(99))
            .andExpect(jsonPath("$.totalAmount").value(32.5));

        verify(orderService).confirmOrder(11L, 99L);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenOrderMissing() throws Exception {
        when(orderService.confirmOrder(eq(999L), eq(99L))).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found")
        );

        mockMvc.perform(put("/api/orders/999/confirm")
                .param("shippingAddressId", "99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void confirmOrderReturnsBadRequestWhenOrderNotPending() throws Exception {
        when(orderService.confirmOrder(eq(11L), eq(99L))).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Only pending orders can be confirmed")
        );

        mockMvc.perform(put("/api/orders/11/confirm")
                .param("shippingAddressId", "99"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void confirmOrderReturnsBadRequestWhenShippingAddressIdMissing() throws Exception {
        mockMvc.perform(put("/api/orders/11/confirm"))
            .andExpect(status().isBadRequest());
    }
}
