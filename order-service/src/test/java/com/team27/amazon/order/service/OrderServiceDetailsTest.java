package com.team27.amazon.order.service;

import com.team27.amazon.order.dto.OrderDetailsDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;
import com.team27.amazon.order.repository.ShipmentJdbcRepository;
import com.team27.amazon.order.repository.ShippingAddressJdbcRepository;
import com.team27.amazon.order.repository.TransactionJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceDetailsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentJdbcRepository shipmentJdbcRepository;

    @Mock
    private ShippingAddressJdbcRepository shippingAddressJdbcRepository;

    @Mock
    private ProductJdbcRepository productJdbcRepository;

    @Mock
    private TransactionJdbcRepository transactionJdbcRepository;

    @InjectMocks
    private OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(1L);
        order.setUserId(5L);
        order.setShippingAddressId(10L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(450.0);
        order.setMetadata(Map.of("source", "WEB"));

        OrderItem item1 = new OrderItem();
        item1.setId(3L);
        item1.setItemOrder(1);
        item1.setProductId(11L);
        item1.setQuantity(2);
        item1.setPriceAtPurchase(100.0);
        item1.setMetadata(Map.of("color", "Black"));

        OrderItem item2 = new OrderItem();
        item2.setId(4L);
        item2.setItemOrder(2);
        item2.setProductId(12L);
        item2.setQuantity(1);
        item2.setPriceAtPurchase(250.0);
        item2.setMetadata(Map.of("size", "L"));

        List<OrderItem> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);
        order.setOrderItems(items);
    }

    @Test
    void shouldReturnOrderDetailsWithSortedItemsAndTotals() {
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

        OrderDetailsDTO result = orderService.getOrderDetails(1L);

        assertNotNull(result);
        assertEquals(1L, result.getOrderId());
        assertEquals(5L, result.getUserId());
        assertEquals(10L, result.getShippingAddressId());
        assertEquals("PENDING", result.getStatus());
        assertEquals(450.0, result.getTotalAmount());
        assertEquals(2, result.getTotalItems());
        assertEquals(3, result.getTotalQuantity());

        assertEquals(2, result.getItems().size());
        assertEquals(1, result.getItems().get(0).getItemOrder());
        assertEquals(2, result.getItems().get(1).getItemOrder());
    }

    @Test
    void shouldReturnEmptyItemsWhenOrderHasNoItems() {
        order.setOrderItems(new ArrayList<>());
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order));

        OrderDetailsDTO result = orderService.getOrderDetails(1L);

        assertNotNull(result);
        assertEquals(0, result.getTotalItems());
        assertEquals(0, result.getTotalQuantity());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void shouldThrow404WhenOrderNotFound() {
        when(orderRepository.findByIdWithItems(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.getOrderDetails(999L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}