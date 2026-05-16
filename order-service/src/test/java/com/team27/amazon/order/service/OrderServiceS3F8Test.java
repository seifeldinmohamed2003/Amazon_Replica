package com.team27.amazon.order.service;

import com.team27.amazon.contracts.dto.ProductDTO;
import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.ShippingServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;
import com.team27.amazon.order.dto.AddOrderItemRequest;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderItemRepository;
import com.team27.amazon.order.repository.OrderRepository;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceS3F8Test {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ShippingServiceClient shippingServiceClient;

    @InjectMocks
    private OrderService orderService;

    private Order pendingOrder;
    private Order deliveredOrder;

    @BeforeEach
    void setUp() {
        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setUserId(10L);
        pendingOrder.setStatus(OrderStatus.PENDING);
        pendingOrder.setOrderItems(new ArrayList<>());

        deliveredOrder = new Order();
        deliveredOrder.setId(2L);
        deliveredOrder.setUserId(10L);
        deliveredOrder.setStatus(OrderStatus.DELIVERED);
        deliveredOrder.setOrderItems(new ArrayList<>());
    }

    @Test
    void shouldAddTwoItemsToPendingOrder() {
        AddOrderItemRequest req1 = new AddOrderItemRequest();
        req1.setProductId(100L);
        req1.setQuantity(2);

        AddOrderItemRequest req2 = new AddOrderItemRequest();
        req2.setProductId(200L);
        req2.setQuantity(1);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(pendingOrder));

        when(productServiceClient.getProduct(100L)).thenReturn(product(100L, 50.0));
        when(productServiceClient.getProduct(200L)).thenReturn(product(200L, 75.0));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.addItemsToExistingOrder(1L, List.of(req1, req2));

        assertEquals(2, result.getOrderItems().size());
        assertEquals(1, result.getOrderItems().get(0).getItemOrder());
        assertEquals(2, result.getOrderItems().get(1).getItemOrder());
    }

    @Test
    void shouldContinueItemOrder() {
        OrderItem existing = new OrderItem();
        existing.setItemOrder(1);
        existing.setProductId(100L);
        existing.setQuantity(1);
        existing.setPriceAtPurchase(30.0);
        existing.setOrder(pendingOrder);

        pendingOrder.getOrderItems().add(existing);

        AddOrderItemRequest req = new AddOrderItemRequest();
        req.setProductId(300L);
        req.setQuantity(4);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(pendingOrder));

        when(productServiceClient.getProduct(300L)).thenReturn(product(300L, 90.0));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.addItemsToExistingOrder(1L, List.of(req));

        assertEquals(2, result.getOrderItems().size());
        assertEquals(2, result.getOrderItems().get(1).getItemOrder());
    }

    @Test
    void shouldThrow400IfNotPending() {
        AddOrderItemRequest req = new AddOrderItemRequest();
        req.setProductId(100L);
        req.setQuantity(1);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(deliveredOrder));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToExistingOrder(2L, List.of(req))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldThrow404IfProductNotFound() {
        AddOrderItemRequest req = new AddOrderItemRequest();
        req.setProductId(999L);
        req.setQuantity(1);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(productServiceClient.getProduct(999L)).thenThrow(mock(feign.FeignException.NotFound.class));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> orderService.addItemsToExistingOrder(1L, List.of(req))
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    private ProductDTO product(Long id, Double price) {
        return new ProductDTO(id, "Product " + id, "Description", price, "CATEGORY", "Brand", 10, "ACTIVE", 0.0, Map.of());
    }
}
