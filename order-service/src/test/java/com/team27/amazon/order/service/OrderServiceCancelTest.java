package com.team27.amazon.order.service;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import com.team27.amazon.order.repository.ProductJdbcRepository;
import com.team27.amazon.order.repository.ShipmentJdbcRepository;
import com.team27.amazon.order.repository.ShippingAddressJdbcRepository;
import com.team27.amazon.order.repository.TransactionJdbcRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceCancelTest {

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

    private Order pendingOrder;
    private Order confirmedOrder;
    private OrderItem firstItem;
    private OrderItem secondItem;

    @BeforeEach
    void setUp() {
        firstItem = firstItem();
        secondItem = secondItem();

        pendingOrder = new Order();
        pendingOrder.setId(11L);
        pendingOrder.setUserId(77L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setMetadata(new HashMap<>());
        pendingOrder.setOrderItems(List.of(firstItem, secondItem));

        confirmedOrder = new Order();
        confirmedOrder.setId(12L);
        confirmedOrder.setUserId(77L);
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);
        confirmedOrder.setMetadata(new HashMap<>());
        confirmedOrder.setOrderItems(List.of(firstItem, secondItem));
    }

    @Test
    void cancelOrderCancelsPendingOrderSuccessfully() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        Order result = orderService.cancelOrder(11L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).save(pendingOrder);
        verifyNoInteractions(productJdbcRepository);
    }

    @Test
    void cancelOrderCancelsConfirmedOrderAndRestoresStock() {
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));
        when(orderRepository.save(confirmedOrder)).thenReturn(confirmedOrder);

        Order result = orderService.cancelOrder(12L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(productJdbcRepository).restoreStockQuantity(201L, 2);
        verify(productJdbcRepository).restoreStockQuantity(202L, 1);
        verify(orderRepository).save(confirmedOrder);
    }

    @Test
    void cancelOrderReturnsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(999L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(productJdbcRepository, shipmentJdbcRepository, shippingAddressJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void cancelOrderReturnsBadRequestWhenOrderIsShipped() {
        confirmedOrder.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(12L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productJdbcRepository, shipmentJdbcRepository, shippingAddressJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void cancelOrderReturnsBadRequestWhenOrderIsDelivered() {
        confirmedOrder.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(12L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productJdbcRepository, shipmentJdbcRepository, shippingAddressJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void cancelOrderReturnsBadRequestWhenOrderIsAlreadyCancelled() {
        confirmedOrder.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.cancelOrder(12L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(productJdbcRepository, shipmentJdbcRepository, shippingAddressJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void cancelOrderHandlesOrderWithNoItems() {
        confirmedOrder.setOrderItems(null);
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));
        when(orderRepository.save(confirmedOrder)).thenReturn(confirmedOrder);

        Order result = orderService.cancelOrder(12L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).save(confirmedOrder);
        verifyNoInteractions(productJdbcRepository);
    }

    @Test
    void cancelOrderHandlesOrderWithEmptyItemsList() {
        confirmedOrder.setOrderItems(List.of());
        when(orderRepository.findById(12L)).thenReturn(java.util.Optional.of(confirmedOrder));
        when(orderRepository.save(confirmedOrder)).thenReturn(confirmedOrder);

        Order result = orderService.cancelOrder(12L);

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository).save(confirmedOrder);
        verifyNoInteractions(productJdbcRepository);
    }

    private OrderItem firstItem() {
        OrderItem item = new OrderItem();
        item.setId(1L);
        item.setProductId(201L);
        item.setQuantity(2);
        item.setPriceAtPurchase(12.5);
        item.setItemOrder(1);
        item.setMetadata(new HashMap<>());
        return item;
    }

    private OrderItem secondItem() {
        OrderItem item = new OrderItem();
        item.setId(2L);
        item.setProductId(202L);
        item.setQuantity(1);
        item.setPriceAtPurchase(7.5);
        item.setItemOrder(2);
        item.setMetadata(new HashMap<>());
        return item;
    }
}