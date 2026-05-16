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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.dto.ShippingAddressDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceConfirmTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private ShippingServiceClient shippingServiceClient;

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
        when(userServiceClient.getShippingAddress(77L, 99L)).thenReturn(new ShippingAddressDTO(99L, 77L, "Home", "Cairo", "Egypt", "street", "notes"));
        when(productServiceClient.getProduct(201L)).thenReturn(product(201L, 10, 12.5));
        when(productServiceClient.getProduct(202L)).thenReturn(product(202L, 5, 7.5));
        when(orderRepository.save(pendingOrder)).thenReturn(pendingOrder);

        Order result = orderService.confirmOrder(11L, 99L);

        assertEquals(OrderStatus.CONFIRMED, result.getStatus());
        assertEquals(99L, result.getShippingAddressId());
        assertEquals(32.5, result.getTotalAmount());
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
        verifyNoInteractions(userServiceClient, productServiceClient, shippingServiceClient);
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
        verifyNoInteractions(userServiceClient, productServiceClient, shippingServiceClient);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenShippingAddressMissing() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(userServiceClient.getShippingAddress(77L, 99L)).thenThrow(mock(feign.FeignException.NotFound.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(productServiceClient, shippingServiceClient);
    }

    @Test
    void confirmOrderReturnsNotFoundWhenProductMissing() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(userServiceClient.getShippingAddress(77L, 99L)).thenReturn(new ShippingAddressDTO(99L, 77L, "Home", "Cairo", "Egypt", "street", "notes"));
        when(productServiceClient.getProduct(201L)).thenThrow(mock(feign.FeignException.NotFound.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
    }

    @Test
    void confirmOrderReturnsBadRequestWhenStockInsufficient() {
        when(orderRepository.findById(11L)).thenReturn(java.util.Optional.of(pendingOrder));
        when(userServiceClient.getShippingAddress(77L, 99L)).thenReturn(new ShippingAddressDTO(99L, 77L, "Home", "Cairo", "Egypt", "street", "notes"));
        when(productServiceClient.getProduct(201L)).thenReturn(product(201L, 1, 12.5));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> orderService.confirmOrder(11L, 99L)
        );

        assertEquals(400, exception.getStatusCode().value());
        verify(orderRepository, never()).save(pendingOrder);
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

    private ProductDTO product(Long id, Integer stock, Double price) {
        return new ProductDTO(id, "Product " + id, "Description", price, "CATEGORY", "Brand", stock, "ACTIVE", 0.0, new HashMap<>());
    }
}
