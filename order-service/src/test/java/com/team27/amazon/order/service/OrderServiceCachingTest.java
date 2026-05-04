package com.team27.amazon.order.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.dto.AddOrderItemRequest;
import com.team27.amazon.order.dto.OrderAnalyticsDTO;
import com.team27.amazon.order.dto.OrderEstimateDTO;
import com.team27.amazon.order.dto.OrderEstimateItemRequestDTO;
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
import org.mockito.ArgumentMatchers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceCachingTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ShipmentJdbcRepository shipmentJdbcRepository;

    @Mock
    private ShippingAddressJdbcRepository shippingAddressJdbcRepository;

    @Mock
    private ProductJdbcRepository productJdbcRepository;

    @Mock
    @SuppressWarnings("unused")
    private TransactionJdbcRepository transactionJdbcRepository;

    @Mock
    private OrderRedisCacheService orderRedisCacheService;

    @InjectMocks
    private OrderService orderService;

    private List<Order> sampleOrders;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        sampleOrders = new ArrayList<>();
        Order order = new Order();
        order.setId(1L);
        order.setUserId(10L);
        order.setStatus(OrderStatus.DELIVERED);
        order.setTotalAmount(120.0);
        order.setOrderedAt(LocalDateTime.of(2026, 3, 10, 10, 0));
        sampleOrders.add(order);
    }

    @Test
    void s3f1SearchOrdersCacheMissCachesForFiveMinutes() {
        when(orderRedisCacheService.orderedParams(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
        when(orderRedisCacheService.featureKey(eq("S3-F1"), any())).thenReturn("order-service::S3-F1::hash");
        when(orderRedisCacheService.get(eq("order-service::S3-F1::hash"), ArgumentMatchers.<TypeReference<List<Order>>>any())).thenReturn(Optional.empty());
        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(sampleOrders);

        List<Order> result = orderService.searchOrders(OrderStatus.DELIVERED, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertEquals(1, result.size());
        verify(orderRepository).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(orderRedisCacheService).set(eq("order-service::S3-F1::hash"), eq(result), eq(Duration.ofMinutes(5)));
    }

    @Test
    void s3f1SearchOrdersCacheHitSkipsRepository() {
        when(orderRedisCacheService.orderedParams(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
        when(orderRedisCacheService.featureKey(eq("S3-F1"), any())).thenReturn("order-service::S3-F1::hash");
        when(orderRedisCacheService.get(eq("order-service::S3-F1::hash"), ArgumentMatchers.<TypeReference<List<Order>>>any())).thenReturn(Optional.of(sampleOrders));

        List<Order> result = orderService.searchOrders(OrderStatus.DELIVERED, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertSame(sampleOrders, result);
        verify(orderRepository, never()).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
        verify(orderRedisCacheService, never()).set(eq("order-service::S3-F1::hash"), any(), any(Duration.class));
    }

    @Test
    void s3f3EstimateCacheMissCachesForFiveMinutes() {
        OrderEstimateItemRequestDTO request = new OrderEstimateItemRequestDTO();
        request.setProductId(99L);
        request.setQuantity(2);

        when(orderRedisCacheService.featureKey(eq("S3-F3"), any())).thenReturn("order-service::S3-F3::hash");
        when(orderRedisCacheService.get("order-service::S3-F3::hash", OrderEstimateDTO.class)).thenReturn(Optional.empty());
        when(productJdbcRepository.findCurrentPriceByProductId(99L)).thenReturn(100.0);

        OrderEstimateDTO dto = orderService.estimateOrderPrice(List.of(request));

        assertEquals(200.0, dto.getSubtotal(), 0.0001);
        verify(orderRedisCacheService).set(eq("order-service::S3-F3::hash"), eq(dto), eq(Duration.ofMinutes(5)));
    }

    @Test
    void s3f3EstimateCacheHitSkipsComputationReads() {
        OrderEstimateDTO cached = OrderEstimateDTO.builder()
                .itemCount(2)
                .subtotal(200.0)
                .shippingCost(50.0)
                .estimatedTotal(250.0)
                .discountApplied(0.0)
                .build();

        when(orderRedisCacheService.featureKey(eq("S3-F3"), any())).thenReturn("order-service::S3-F3::hash");
        when(orderRedisCacheService.get("order-service::S3-F3::hash", OrderEstimateDTO.class)).thenReturn(Optional.of(cached));

        OrderEstimateItemRequestDTO request = new OrderEstimateItemRequestDTO();
        request.setProductId(99L);
        request.setQuantity(2);

        OrderEstimateDTO result = orderService.estimateOrderPrice(List.of(request));

        assertSame(cached, result);
        verify(productJdbcRepository, never()).findCurrentPriceByProductId(anyLong());
    }

    @Test
    void s3f5MetadataFilterCachingUsesFiveMinutes() {
        when(orderRedisCacheService.orderedParams(any(), any(), any(), any())).thenReturn(Map.of());
        when(orderRedisCacheService.featureKey(eq("S3-F5"), any())).thenReturn("order-service::S3-F5::hash");
        when(orderRedisCacheService.get(eq("order-service::S3-F5::hash"), ArgumentMatchers.<TypeReference<List<Order>>>any())).thenReturn(Optional.empty());
        when(orderRepository.findByMetadataField("source", "campaign-a")).thenReturn(sampleOrders);

        List<Order> result = orderService.searchOrdersByMetadata("source", "campaign-a");

        assertEquals(1, result.size());
        verify(orderRedisCacheService).set(eq("order-service::S3-F5::hash"), eq(result), eq(Duration.ofMinutes(5)));
    }

    @Test
    void s3f6OldAnalyticsCachingUsesTenMinutesAndOldDto() {
        when(orderRedisCacheService.orderedParams(any(), any(), any(), any())).thenReturn(Map.of());
        when(orderRedisCacheService.featureKey(eq("S3-F6"), any())).thenReturn("order-service::S3-F6::hash");
        when(orderRedisCacheService.get("order-service::S3-F6::hash", OrderAnalyticsDTO.class)).thenReturn(Optional.empty());
        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(sampleOrders);

        OrderAnalyticsDTO dto = orderService.getOrderAnalytics(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 3, 31, 23, 59));

        assertEquals(1, dto.getTotalOrders());
        verify(orderRedisCacheService).set(eq("order-service::S3-F6::hash"), eq(dto), eq(Duration.ofMinutes(10)));
    }

    @Test
    void s3f9OrderDetailsCachingUsesTenMinutes() {
        Order order = new Order();
        order.setId(77L);
        order.setUserId(55L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setOrderItems(new ArrayList<>());

        when(orderRedisCacheService.get("order-service::S3-F9::77", com.team27.amazon.order.dto.OrderDetailsDTO.class))
                .thenReturn(Optional.empty());
        when(orderRepository.findByIdWithItems(77L)).thenReturn(Optional.of(order));

        com.team27.amazon.order.dto.OrderDetailsDTO dto = orderService.getOrderDetails(77L);

        assertEquals(77L, dto.getOrderId());
        verify(orderRedisCacheService).set(eq("order-service::S3-F9::77"), eq(dto), eq(Duration.ofMinutes(10)));
    }

    @Test
    void orderGetByIdCachingUsesEntityKeyAndFifteenMinutes() {
        Order order = new Order();
        order.setId(42L);

        when(orderRedisCacheService.get("order-service::order::42", Order.class)).thenReturn(Optional.empty());
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.getOrderById(42L);

        assertEquals(42L, result.orElseThrow().getId());
        verify(orderRedisCacheService).set("order-service::order::42", order, Duration.ofMinutes(15));
    }

    @Test
    void getAllOrdersRemainsUncached() {
        when(orderRepository.findAll()).thenReturn(sampleOrders);

        List<Order> result = orderService.getAllOrders();

        assertEquals(1, result.size());
        verifyNoInteractions(orderRedisCacheService);
    }

    @Test
    void confirmOrderInvalidatesRequiredOrderFeatureCaches() {
        Order order = new Order();
        order.setId(10L);
        order.setUserId(99L);
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setProductId(901L);
        item.setQuantity(1);
        item.setPriceAtPurchase(100.0);
        order.setOrderItems(List.of(item));

        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(shippingAddressJdbcRepository.existsByShippingAddressId(77L)).thenReturn(true);
        when(productJdbcRepository.existsByProductId(901L)).thenReturn(true);
        when(productJdbcRepository.findStockQuantityByProductId(901L)).thenReturn(10);
        when(productJdbcRepository.deductStockQuantity(901L, 1)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRedisCacheService.orderKey(10L)).thenReturn("order-service::order::10");

        Order confirmed = orderService.confirmOrder(10L, 77L);

        assertEquals(OrderStatus.CONFIRMED, confirmed.getStatus());
        verify(orderRedisCacheService).delete("order-service::order::10");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F1::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F3::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F5::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F6::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F9::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F10::*");
        verify(orderRedisCacheService).deleteByPattern("product-service::S2-F12::*");
    }

    @Test
    void redisGetFailureDoesNotBreakS3f1ReadPath() {
        when(orderRedisCacheService.orderedParams(any(), any(), any(), any(), any(), any())).thenReturn(Map.of());
        when(orderRedisCacheService.featureKey(eq("S3-F1"), any())).thenReturn("order-service::S3-F1::hash");
        when(orderRedisCacheService.get(eq("order-service::S3-F1::hash"), ArgumentMatchers.<TypeReference<List<Order>>>any())).thenThrow(new RuntimeException("redis error"));
        when(orderRepository.findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(sampleOrders);

        assertDoesNotThrow(() -> orderService.searchOrders(OrderStatus.DELIVERED, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        verify(orderRepository).findByOrderedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void deliverOrderInvalidatesRequiredCaches() {
        Order order = new Order();
        order.setId(123L);
        order.setUserId(90L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTotalAmount(200.0);

        when(orderRepository.findById(123L)).thenReturn(Optional.of(order));
        when(shipmentJdbcRepository.existsByOrderId(123L)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRedisCacheService.orderKey(123L)).thenReturn("order-service::order::123");

        Order result = orderService.deliverOrder(123L);

        assertEquals(OrderStatus.DELIVERED, result.getStatus());
        verify(orderRedisCacheService).delete("order-service::order::123");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F1::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F3::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F5::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F6::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F9::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F10::*");
    }

    @Test
    void cancelOrderInvalidatesRequiredCaches() {
        Order order = new Order();
        order.setId(321L);
        order.setUserId(44L);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(321L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRedisCacheService.orderKey(321L)).thenReturn("order-service::order::321");

        Order cancelled = orderService.cancelOrder(321L);

        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        verify(orderRedisCacheService).delete("order-service::order::321");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F10::*");
    }

    @Test
    void addItemsInvalidatesOrderAndItemRelatedCaches() {
        Order pending = new Order();
        pending.setId(500L);
        pending.setStatus(OrderStatus.PENDING);
        pending.setOrderItems(new ArrayList<>());

        AddOrderItemRequest request = new AddOrderItemRequest();
        request.setProductId(1000L);
        request.setQuantity(1);

        when(orderRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(productJdbcRepository.existsByProductId(1000L)).thenReturn(true);
        when(productJdbcRepository.findCurrentPriceByProductId(1000L)).thenReturn(25.0);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRedisCacheService.orderKey(500L)).thenReturn("order-service::order::500");

        Order result = orderService.addItemsToExistingOrder(500L, List.of(request));

        assertEquals(1, result.getOrderItems().size());
        verify(orderRedisCacheService).delete("order-service::order::500");
        verify(orderRedisCacheService, atLeastOnce()).deleteByPattern("order-service::S3-F3::*");
        verify(orderRedisCacheService, atLeastOnce()).deleteByPattern("order-service::S3-F6::*");
        verify(orderRedisCacheService, atLeastOnce()).deleteByPattern("order-service::S3-F9::*");
        verify(orderRedisCacheService, atLeastOnce()).deleteByPattern("order-service::S3-F10::*");
        verify(orderRedisCacheService).deleteByPattern("product-service::S2-F12::*");
    }
}
