package com.team27.amazon.order.controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.AddOrderItemRequestDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerAddItemsTest {

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
    void addItemsToOrderReturnsUpdatedOrder() throws Exception {
        Order updatedOrder = new Order();
        updatedOrder.setId(11L);
        updatedOrder.setUserId(77L);
        updatedOrder.setStatus(OrderStatus.PENDING);
        updatedOrder.setMetadata(new HashMap<>());

        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setProductId(201L);
        item1.setQuantity(2);
        item1.setPriceAtPurchase(12.5);
        item1.setItemOrder(1);
        item1.setMetadata(new HashMap<>());

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setProductId(202L);
        item2.setQuantity(1);
        item2.setPriceAtPurchase(7.5);
        item2.setItemOrder(2);
        item2.setMetadata(new HashMap<>());

        updatedOrder.setOrderItems(List.of(item1, item2));

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>()),
                new AddOrderItemRequestDTO(202L, 1, new HashMap<>())
        );

        when(orderService.addItemsToOrder(anyLong(), anyList())).thenReturn(updatedOrder);

        String requestBody = """
                [
                    {"productId": 201, "quantity": 2, "metadata": {}},
                    {"productId": 202, "quantity": 1, "metadata": {}}
                ]
                """;

        mockMvc.perform(post("/api/orders/11/items")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(11))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.orderItems[0].productId").value(201))
            .andExpect(jsonPath("$.orderItems[0].itemOrder").value(1))
            .andExpect(jsonPath("$.orderItems[1].productId").value(202))
            .andExpect(jsonPath("$.orderItems[1].itemOrder").value(2));

        verify(orderService).addItemsToOrder(anyLong(), anyList());
    }

    @Test
    void addItemsToOrderReturnsNotFoundWhenOrderMissing() throws Exception {
        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        when(orderService.addItemsToOrder(eq(999L), anyList())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Order not found")
        );

        String requestBody = """
                [
                    {"productId": 201, "quantity": 2, "metadata": {}}
                ]
                """;

        mockMvc.perform(post("/api/orders/999/items")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isNotFound());
    }

    @Test
    void addItemsToOrderReturnsBadRequestWhenOrderNotPending() throws Exception {
        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        when(orderService.addItemsToOrder(eq(11L), anyList())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Cannot add items to orders that are not pending")
        );

        String requestBody = """
                [
                    {"productId": 201, "quantity": 2, "metadata": {}}
                ]
                """;

        mockMvc.perform(post("/api/orders/11/items")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }

    @Test
    void addItemsToOrderReturnsNotFoundWhenProductMissing() throws Exception {
        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(999L, 2, new HashMap<>())
        );

        when(orderService.addItemsToOrder(eq(11L), anyList())).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Product not found")
        );

        String requestBody = """
                [
                    {"productId": 999, "quantity": 2, "metadata": {}}
                ]
                """;

        mockMvc.perform(post("/api/orders/11/items")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isNotFound());
    }

    @Test
    void addItemsToOrderReturnsBadRequestWhenItemsEmpty() throws Exception {
        when(orderService.addItemsToOrder(eq(11L), eq(Collections.emptyList()))).thenThrow(
                new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Items list must not be empty")
        );

        String requestBody = "[]";

        mockMvc.perform(post("/api/orders/11/items")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isBadRequest());
    }
}