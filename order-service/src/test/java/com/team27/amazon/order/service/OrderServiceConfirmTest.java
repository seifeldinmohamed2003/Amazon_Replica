package com.team27.amazon.order.service;

import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;

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
import static org.mockito.ArgumentMatchers.any;
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
import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ShippingAddressDTO;
import com.team27.amazon.order.messaging.publishers.OrderEventPublisher;

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



    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private OrderEventPublisher orderEventPublisher;

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
        when(userServiceClient.getShippingAddress(pendingOrder.getUserId(), 99L))
            .thenReturn(new ShippingAddressDTO(99L, pendingOrder.getUserId(), "a", "b", "g", "p", "c"));

        ProductDTO p201 = new ProductDTO(201L, "P201", "desc", 12.5, "CAT", "Brand", 10, "ACTIVE", 4.5, java.util.Map.of());
        ProductDTO p202 = new ProductDTO(202L, "P202", "desc", 7.5, "CAT", "Brand", 5, "ACTIVE", 4.5, java.util.Map.of());
        when(productServiceClient.getProductsBatch(java.util.List.of(201L, 202L)))
            .thenReturn(java.util.List.of(p201, p202));
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        Order result = orderService.confirmOrder(11L, 99L);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(99L, result.getShippingAddressId());
        assertEquals(32.5, result.getTotalAmount());
        verify(orderEventPublisher).publishOrderPlaced(any());
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
        verifyNoInteractions(userServiceClient, productServiceClient, transactionJdbcRepository);
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
        when(userServiceClient.getShippingAddress(pendingOrder.getUserId(), 99L)).thenThrow(new RuntimeException("not found"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(productServiceClient, transactionJdbcRepository);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenProductMissing() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(userServiceClient.getShippingAddress(pendingOrder.getUserId(), 99L))
            .thenReturn(new ShippingAddressDTO(99L, pendingOrder.getUserId(), "a", "b", "g", "p", "c"));
        when(productServiceClient.getProductsBatch(java.util.List.of(201L, 202L)))
            .thenReturn(java.util.List.of(/* missing 201 intentionally */));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void confirmOrderReturnsBadRequestWhenStockInsufficient() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(userServiceClient.getShippingAddress(pendingOrder.getUserId(), 99L))
            .thenReturn(new ShippingAddressDTO(99L, pendingOrder.getUserId(), "a", "b", "g", "p", "c"));
        ProductDTO lowStock = new ProductDTO(201L, "P201", "desc", 12.5, "CAT", "Brand", 1, "ACTIVE", 4.5, java.util.Map.of());
        when(productServiceClient.getProductsBatch(java.util.List.of(201L, 202L)))
            .thenReturn(java.util.List.of(lowStock));

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
