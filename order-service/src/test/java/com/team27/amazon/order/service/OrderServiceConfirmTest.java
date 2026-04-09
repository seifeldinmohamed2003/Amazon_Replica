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
class OrderServiceConfirmTest {

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
    private OrderItem firstItem;
    private OrderItem secondItem;

    @BeforeEach
    void setUp() {
        pendingOrder = new Order();
        pendingOrder.setId(11L);
        pendingOrder.setUserId(77L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setMetadata(new HashMap<>());
        pendingOrder.setOrderItems(List.of(firstItem(), secondItem()));

        firstItem = firstItem();
        secondItem = secondItem();
        pendingOrder.setOrderItems(List.of(firstItem, secondItem));
    }

    @Test
    void confirmOrderConfirmsOrderDeductsStockAndCalculatesTotal() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(shippingAddressJdbcRepository.existsByShippingAddressId(99L)).thenReturn(true);
        when(productJdbcRepository.existsByProductId(201L)).thenReturn(true);
        when(productJdbcRepository.existsByProductId(202L)).thenReturn(true);
        when(productJdbcRepository.findStockQuantityByProductId(201L)).thenReturn(10);
        when(productJdbcRepository.findStockQuantityByProductId(202L)).thenReturn(5);
        when(productJdbcRepository.deductStockQuantity(201L, 2)).thenReturn(1);
        when(productJdbcRepository.deductStockQuantity(202L, 1)).thenReturn(1);
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        Order result = orderService.confirmOrder(11L, 99L);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(99L, result.getShippingAddressId());
        assertEquals(32.5, result.getTotalAmount());
        verify(productJdbcRepository).deductStockQuantity(201L, 2);
        verify(productJdbcRepository).deductStockQuantity(202L, 1);
        verify(orderRepository).save(pendingOrder);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenOrderDoesNotExist() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(shippingAddressJdbcRepository, productJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void confirmOrderReturnsBadRequestWhenOrderIsNotPending() {
        pendingOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(shippingAddressJdbcRepository, productJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenShippingAddressMissing() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(shippingAddressJdbcRepository.existsByShippingAddressId(99L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(productJdbcRepository, transactionJdbcRepository);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenProductMissing() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(shippingAddressJdbcRepository.existsByShippingAddressId(99L)).thenReturn(true);
        when(productJdbcRepository.existsByProductId(201L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(productJdbcRepository, never()).deductStockQuantity(201L, 2);
    }

    @Test
    void confirmOrderReturnsBadRequestWhenStockInsufficient() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(shippingAddressJdbcRepository.existsByShippingAddressId(99L)).thenReturn(true);
        when(productJdbcRepository.existsByProductId(201L)).thenReturn(true);
        when(productJdbcRepository.findStockQuantityByProductId(201L)).thenReturn(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(transactionJdbcRepository);
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
