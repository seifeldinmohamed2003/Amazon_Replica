package com.team27.amazon.order.controller;

import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderControllerDetailsTest {

    @Test
    void testGetOrderDetails() {
        OrderService service = Mockito.mock(OrderService.class);
        OrderController controller = new OrderController(service);

        OrderDetailsDTO dto = new OrderDetailsDTO();
        dto.setOrderId(1L);

        Mockito.when(service.getOrderDetails(1L)).thenReturn(dto);

        OrderDetailsDTO response = controller.getOrderDetails(1L);

        assertEquals(1L, response.getOrderId());
    }
}