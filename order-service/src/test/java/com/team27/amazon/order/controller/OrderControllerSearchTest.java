package com.team27.amazon.order.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerSearchTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController orderController = new OrderController();
        ReflectionTestUtils.setField(orderController, "orderService", orderService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController).build();
    }

    @Test
    void searchOrdersWithStatusReturnsMatchingOrders() throws Exception {
        Order latest = order(5L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 28, 11, 30));
        Order earlier = order(2L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 4, 9, 0));

        when(orderService.searchOrders(
            eq(OrderStatus.DELIVERED),
            eq(LocalDate.of(2026, 3, 1)),
            eq(LocalDate.of(2026, 3, 31))))
            .thenReturn(List.of(latest, earlier));

        mockMvc.perform(get("/api/orders/search")
                .param("status", "DELIVERED")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(5))
            .andExpect(jsonPath("$[1].id").value(2));

        verify(orderService).searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));
    }

    @Test
    void searchOrdersWithoutStatusPassesNullStatusToService() throws Exception {
        Order latest = order(5L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 28, 11, 30));
        Order middle = order(3L, OrderStatus.PENDING, LocalDateTime.of(2026, 3, 18, 15, 0));
        Order earlier = order(2L, OrderStatus.DELIVERED, LocalDateTime.of(2026, 3, 4, 9, 0));

        when(orderService.searchOrders(
            null,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31)))
            .thenReturn(List.of(latest, middle, earlier));

        mockMvc.perform(get("/api/orders/search")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value(5))
            .andExpect(jsonPath("$[1].id").value(3))
            .andExpect(jsonPath("$[2].id").value(2));

        verify(orderService).searchOrders(
            null,
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 31));
    }

    @Test
    void searchOrdersReturnsBadRequestWhenStartDateMissing() throws Exception {
        mockMvc.perform(get("/api/orders/search")
                .param("status", "DELIVERED")
                .param("endDate", "2026-03-31"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).searchOrders(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchOrdersReturnsBadRequestWhenEndDateMissing() throws Exception {
        mockMvc.perform(get("/api/orders/search")
                .param("status", "DELIVERED")
                .param("startDate", "2026-03-01"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).searchOrders(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchOrdersReturnsBadRequestWhenStatusInvalid() throws Exception {
        mockMvc.perform(get("/api/orders/search")
                .param("status", "NOT_A_STATUS")
                .param("startDate", "2026-03-01")
                .param("endDate", "2026-03-31"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).searchOrders(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchOrdersReturnsBadRequestWhenDateFormatInvalid() throws Exception {
        mockMvc.perform(get("/api/orders/search")
                .param("status", "DELIVERED")
                .param("startDate", "03-01-2026")
                .param("endDate", "2026-03-31"))
            .andExpect(status().isBadRequest());

        verify(orderService, never()).searchOrders(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchOrdersWithStartDateAfterEndDateReturnsOkAndEmptyList() throws Exception {
        when(orderService.searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 1)))
            .thenReturn(List.of());

        mockMvc.perform(get("/api/orders/search")
                .param("status", "DELIVERED")
                .param("startDate", "2026-04-10")
                .param("endDate", "2026-04-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        verify(orderService).searchOrders(
            OrderStatus.DELIVERED,
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 4, 1));
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