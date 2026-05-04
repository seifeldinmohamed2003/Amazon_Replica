package com.team27.amazon.order.service;

import com.team27.amazon.order.dto.OrderAnalyticsDashboardDTO;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceS3F10Test {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private com.team27.amazon.common.events.MongoEventLogger orderEventLogger; // stub only

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private List<Order> marchOrders;

    @BeforeEach
    void setUp() {
        marchOrders = new ArrayList<>();

        // 6 DELIVERED with totalAmount sum 3000 (e.g., each 500)
        for (int i = 0; i < 6; i++) {
            Order o = new Order();
            o.setId((long) i + 1);
            o.setStatus(OrderStatus.DELIVERED);
            o.setTotalAmount(500.0);
            o.setOrderedAt(LocalDateTime.of(2026, 3, 5 + i, 10, 0));
            marchOrders.add(o);
        }

        // 2 CANCELLED
        for (int i = 0; i < 2; i++) {
            Order o = new Order();
            o.setId((long) 100 + i);
            o.setStatus(OrderStatus.CANCELLED);
            o.setTotalAmount(null);
            o.setOrderedAt(LocalDateTime.of(2026, 3, 15 + i, 11, 0));
            marchOrders.add(o);
        }

        // 1 RETURNED
        Order returned = new Order();
        returned.setId(200L);
        returned.setStatus(OrderStatus.RETURNED);
        returned.setTotalAmount(null);
        returned.setOrderedAt(LocalDateTime.of(2026, 3, 20, 12, 0));
        marchOrders.add(returned);

        // 1 PENDING
        Order pending = new Order();
        pending.setId(300L);
        pending.setStatus(OrderStatus.PENDING);
        pending.setTotalAmount(null);
        pending.setOrderedAt(LocalDateTime.of(2026, 3, 25, 13, 0));
        marchOrders.add(pending);
    }

    private void registerObserver() {
        // ensure the mocked observer is registered so notifyObservers triggers it
        orderService.register(orderEventLogger);
    }

    @Test
    void cacheHitReturnsCachedDtoAndDoesNotCallRepositoryButStillNotifiesObserver() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        String cacheKey = "order-service::S3-F10::" + start.toString() + "_" + end.toString();

        OrderAnalyticsDashboardDTO cachedDto = OrderAnalyticsDashboardDTO.builder()
                .totalOrders(5)
                .totalRevenue(100.0)
                .averageOrderValue(20.0)
                .completionRate(0.2)
                .ordersByStatus(Map.of("DELIVERED", 2L))
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn("cached-json");
        when(objectMapper.readValue("cached-json", OrderAnalyticsDashboardDTO.class)).thenReturn(cachedDto);

        registerObserver();

        OrderAnalyticsDashboardDTO result = orderService.getOrderAnalyticsDashboard(start, end);

        assertSame(cachedDto, result);
        verify(orderRepository, never()).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        verify(orderEventLogger).onEvent(eq("ANALYTICS_VIEWED"), any());
    }

    @Test
    void cacheMissTriggersDbAndWritesToRedisAndNotifiesObserver() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        String cacheKey = "order-service::S3-F10::" + start.toString() + "_" + end.toString();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(marchOrders);

        // objectMapper serialization for cache set
        when(objectMapper.writeValueAsString(any(OrderAnalyticsDashboardDTO.class))).thenReturn("json-payload");

        doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        registerObserver();

        OrderAnalyticsDashboardDTO dto = orderService.getOrderAnalyticsDashboard(start, end);

        assertEquals(10, dto.getTotalOrders());
        verify(orderRepository).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(valueOperations).set(eq(cacheKey), eq("json-payload"), any(Duration.class));
        verify(orderEventLogger).onEvent(eq("ANALYTICS_VIEWED"), any());
    }

    @Test
    void redisGetFailureIsSoftAndDbUsedAndObserverNotified() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        String cacheKey = "order-service::S3-F10::" + start.toString() + "_" + end.toString();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenThrow(new RuntimeException("redis-down"));

        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(marchOrders);

        registerObserver();

        OrderAnalyticsDashboardDTO dto = orderService.getOrderAnalyticsDashboard(start, end);

        assertEquals(10, dto.getTotalOrders());
        verify(orderRepository).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(orderEventLogger).onEvent(eq("ANALYTICS_VIEWED"), any());
    }

    @Test
    void redisSetFailureIsSoftAndDtoStillReturnedAndObserverNotified() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        String cacheKey = "order-service::S3-F10::" + start.toString() + "_" + end.toString();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(marchOrders);

        when(objectMapper.writeValueAsString(any(OrderAnalyticsDashboardDTO.class))).thenReturn("json-payload");
        doThrow(new RuntimeException("redis-set-fail")).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        registerObserver();

        OrderAnalyticsDashboardDTO dto = orderService.getOrderAnalyticsDashboard(start, end);

        assertEquals(10, dto.getTotalOrders());
        verify(orderRepository).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(orderEventLogger).onEvent(eq("ANALYTICS_VIEWED"), any());
    }

    @Test
    void observerPayloadContainsExpectedFields() throws Exception {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(marchOrders);

        when(objectMapper.writeValueAsString(any(OrderAnalyticsDashboardDTO.class))).thenReturn("json-payload");
        doNothing().when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        registerObserver();

        orderService.getOrderAnalyticsDashboard(start, end);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(orderEventLogger).onEvent(eq("ANALYTICS_VIEWED"), payloadCaptor.capture());

        Object payload = payloadCaptor.getValue();
        assertTrue(payload instanceof java.util.Map);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) payload;
        assertEquals(0L, ((Number) map.get("orderId")).longValue());
        assertTrue(map.containsKey("details"));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> details = (java.util.Map<String, Object>) map.get("details");
        assertEquals("2026-03-01", details.get("startDate"));
        assertEquals("2026-03-31", details.get("endDate"));
        assertEquals("S3-F10", details.get("featureId"));
        assertEquals("/api/orders/analytics/dashboard", details.get("endpoint"));
    }

    @Test
    void shouldComputeDashboardAggregatesForMarch2026() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(marchOrders);

        OrderAnalyticsDashboardDTO dto = orderService.getOrderAnalyticsDashboard(start, end);

        assertEquals(10, dto.getTotalOrders());
        assertEquals(3000.0, dto.getTotalRevenue(), 0.0001);
        assertEquals(300.0, dto.getAverageOrderValue(), 0.0001); // 3000/10
        assertEquals(0.6, dto.getCompletionRate(), 0.0001);

        Map<String, Long> byStatus = dto.getOrdersByStatus();
        assertEquals(6L, byStatus.get("DELIVERED"));
        assertEquals(2L, byStatus.get("CANCELLED"));
        assertEquals(1L, byStatus.get("RETURNED"));
        assertEquals(1L, byStatus.get("PENDING"));
    }

    @Test
    void shouldReturnZerosForEmptyRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        OrderAnalyticsDashboardDTO dto = orderService.getOrderAnalyticsDashboard(start, end);

        assertEquals(0, dto.getTotalOrders());
        assertEquals(0.0, dto.getTotalRevenue(), 0.0001);
        assertEquals(0.0, dto.getAverageOrderValue(), 0.0001);
        assertEquals(0.0, dto.getCompletionRate(), 0.0001);
    }

    @Test
    void shouldThrow400OnInvalidDateRange() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 3, 1);

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            orderService.getOrderAnalyticsDashboard(start, end);
        });
    }
}
