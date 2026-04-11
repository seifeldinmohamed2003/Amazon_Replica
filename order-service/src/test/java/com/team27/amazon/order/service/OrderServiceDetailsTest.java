package com.team27.amazon.order.service;

import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceDetailsTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = new OrderService(orderRepository);
    }

    @Test
    void getOrderDetails_shouldReturnItemsOrderedAndCorrectTotals() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(7L);
        order.setShippingAddressId(3L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(450.0);

        OrderItem item1 = new OrderItem();
        item1.setId(101L);
        item1.setItemOrder(2);
        item1.setProductId(2001L);
        item1.setQuantity(1);
        item1.setPriceAtPurchase(100.0);

        OrderItem item2 = new OrderItem();
        item2.setId(102L);
        item2.setItemOrder(1);
        item2.setProductId(2002L);
        item2.setQuantity(2);
        item2.setPriceAtPurchase(150.0);

        OrderItem item3 = new OrderItem();
        item3.setId(103L);
        item3.setItemOrder(3);
        item3.setProductId(2003L);
        item3.setQuantity(3);
        item3.setPriceAtPurchase(50.0);

        order.setOrderItems(Arrays.asList(item1, item2, item3));

        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

        OrderDetailsDTO result = orderService.getOrderDetails(1L);

        assertEquals(1L, result.getOrderId());
        assertEquals(7L, result.getUserId());
        assertEquals(3L, result.getShippingAddressId());
        assertEquals("PENDING", result.getStatus());
        assertEquals(450.0, result.getTotalAmount());

        assertEquals(3, result.getTotalItems());
        assertEquals(6, result.getTotalQuantity());

        assertEquals(3, result.getItems().size());
        assertEquals(1, result.getItems().get(0).getItemOrder());
        assertEquals(2, result.getItems().get(1).getItemOrder());
        assertEquals(3, result.getItems().get(2).getItemOrder());

        assertEquals(2, result.getItems().get(0).getQuantity());
        assertEquals(1, result.getItems().get(1).getQuantity());
        assertEquals(3, result.getItems().get(2).getQuantity());
    }

    @Test
    void getOrderDetails_shouldReturnEmptyItemsWhenOrderHasNoItems() {
        Order order = new Order();
        order.setId(2L);
        order.setUserId(8L);
        order.setShippingAddressId(4L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(0.0);
        order.setOrderItems(Collections.emptyList());

        when(orderRepository.findByIdWithItems(2L)).thenReturn(Optional.of(order));

        OrderDetailsDTO result = orderService.getOrderDetails(2L);

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getTotalQuantity());
    }

    @Test
    void getOrderDetails_shouldThrow404WhenOrderDoesNotExist() {
        when(orderRepository.findByIdWithItems(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.getOrderDetails(999L)
        );

        assertEquals(HttpStatusCode.valueOf(404), ex.getStatusCode());
    }
}