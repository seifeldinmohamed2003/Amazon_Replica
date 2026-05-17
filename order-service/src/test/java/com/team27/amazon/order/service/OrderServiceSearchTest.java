package com.team27.amazon.order.service;

import com.team27.amazon.contracts.feign.ProductServiceClient;
import com.team27.amazon.contracts.feign.UserServiceClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceSearchTest {

    @Mock
    private OrderRepository orderRepository;



    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void searchOrdersFiltersByStatusAndSortsMostRecentFirst() {
        Order marchLate = order(5L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 28, 11, 30));
        Order marchEarly = order(2L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 4, 9, 0));
        Order marchPending = order(3L, OrderStatus.PENDING, LocalDateTime.of(2026, 3, 18, 15, 0));

        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of(marchEarly, marchPending, marchLate));

        List<Order> results = orderService.searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));

        assertEquals(2, results.size());
        assertEquals(5L, results.get(0).getId());
        assertEquals(2L, results.get(1).getId());

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findByOrderedAtBetween(startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_999_999), endCaptor.getValue());
    }

    @Test
    void searchOrdersWithoutStatusReturnsAllOrdersInRangeMostRecentFirst() {
        Order marchLate = order(5L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 28, 11, 30));
        Order marchEarly = order(2L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 4, 9, 0));
        Order marchPending = order(3L, OrderStatus.PENDING, LocalDateTime.of(2026, 3, 18, 15, 0));

        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of(marchEarly, marchPending, marchLate));

        List<Order> results = orderService.searchOrders(
            null,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));

        assertEquals(3, results.size());
        assertEquals(5L, results.get(0).getId());
        assertEquals(3L, results.get(1).getId());
        assertEquals(2L, results.get(2).getId());
    }

    @Test
    void searchOrdersReturnsEmptyWhenNoOrdersInRange() {
        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of());

        List<Order> results = orderService.searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));

        assertTrue(results.isEmpty());
    }

    @Test
    void searchOrdersReturnsEmptyWhenStatusDoesNotMatchAnyOrder() {
        Order marchPending = order(3L, OrderStatus.PENDING, LocalDateTime.of(2026, 3, 18, 15, 0));

        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 3, 1, 0, 0),
            LocalDateTime.of(2026, 3, 31, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of(marchPending));

        List<Order> results = orderService.searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));

        assertTrue(results.isEmpty());
    }

    @Test
    void searchOrdersUsesWholeDayBoundaries() {
        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 4, 10, 0, 0),
            LocalDateTime.of(2026, 4, 10, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of());

        orderService.searchOrders(
            null,
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 10));

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findByOrderedAtBetween(startCaptor.capture(), endCaptor.capture());
        assertEquals(LocalDateTime.of(2026, 4, 10, 0, 0), startCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 4, 10, 23, 59, 59, 999_999_999), endCaptor.getValue());
    }

    @Test
    void searchOrdersWithStartDateAfterEndDateReturnsEmpty() {
        when(orderRepository.findByOrderedAtBetween(
            LocalDateTime.of(2026, 4, 10, 0, 0),
            LocalDateTime.of(2026, 4, 1, 23, 59, 59, 999_999_999)))
            .thenReturn(List.of());

        List<Order> results = orderService.searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 1));

        assertTrue(results.isEmpty());
    }

    private Order order(Long id, OrderStatus status, LocalDateTime orderedAt) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(100L + id);
        order.setStatus(status);
        order.setOrderedAt(orderedAt);
        order.setMetadata(new HashMap<>());
        return order;
    }
}
