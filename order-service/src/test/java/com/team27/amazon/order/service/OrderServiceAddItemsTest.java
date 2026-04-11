package com.team27.amazon.order.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.dto.AddOrderItemRequestDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceAddItemsTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductJdbcRepository productJdbcRepository;

    @InjectMocks
    private OrderService orderService;

    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = new Order();
        pendingOrder.setId(11L);
        pendingOrder.setUserId(77L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setMetadata(new HashMap<>());
        pendingOrder.setOrderItems(new ArrayList<>());
    }

    @Test
    void addItemsToOrderAddsItemsWithCorrectItemOrder() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(productJdbcRepository.findCurrentPriceByProductId(201L)).thenReturn(12.5);
        when(productJdbcRepository.findCurrentPriceByProductId(202L)).thenReturn(7.5);
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>()),
                new AddOrderItemRequestDTO(202L, 1, new HashMap<>())
        );

        Order result = orderService.addItemsToOrder(11L, requestItems);

        assertEquals(2, result.getOrderItems().size());
        assertEquals(201L, result.getOrderItems().get(0).getProductId());
        assertEquals(1, result.getOrderItems().get(0).getItemOrder());
        assertEquals(12.5, result.getOrderItems().get(0).getPriceAtPurchase());
        assertEquals(202L, result.getOrderItems().get(1).getProductId());
        assertEquals(2, result.getOrderItems().get(1).getItemOrder());
        assertEquals(7.5, result.getOrderItems().get(1).getPriceAtPurchase());
        verify(orderRepository).save(pendingOrder);
    }

    @Test
    void addItemsToOrderContinuesItemOrderFromExistingItems() {
        // Add existing items
        OrderItem existingItem = new OrderItem();
        existingItem.setId(1L);
        existingItem.setProductId(200L);
        existingItem.setQuantity(1);
        existingItem.setPriceAtPurchase(10.0);
        existingItem.setItemOrder(2);
        existingItem.setMetadata(new HashMap<>());
        pendingOrder.setOrderItems(new ArrayList<>(List.of(existingItem)));

        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(productJdbcRepository.findCurrentPriceByProductId(201L)).thenReturn(12.5);
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        Order result = orderService.addItemsToOrder(11L, requestItems);

        assertEquals(2, result.getOrderItems().size());
        assertEquals(3, result.getOrderItems().get(1).getItemOrder()); // Next after max 2
        verify(orderRepository).save(pendingOrder);
    }

    @Test
    void addItemsToOrderReturnsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.empty());

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToOrder(11L, requestItems)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
    }

    @Test
    void addItemsToOrderReturnsBadRequestWhenOrderNotPending() {
        pendingOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToOrder(11L, requestItems)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
    }

    @Test
    void addItemsToOrderReturnsNotFoundWhenProductDoesNotExist() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(productJdbcRepository.findCurrentPriceByProductId(201L)).thenReturn(null);

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(201L, 2, new HashMap<>())
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToOrder(11L, requestItems)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
    }

    @Test
    void addItemsToOrderReturnsBadRequestWhenItemsListEmpty() {
        List<AddOrderItemRequestDTO> requestItems = List.of();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToOrder(11L, requestItems)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(orderRepository, never()).findById(11L);
    }

    @Test
    void addItemsToOrderReturnsBadRequestWhenItemInvalid() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));

        List<AddOrderItemRequestDTO> requestItems = List.of(
                new AddOrderItemRequestDTO(null, 2, new HashMap<>())
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToOrder(11L, requestItems)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
    }
}