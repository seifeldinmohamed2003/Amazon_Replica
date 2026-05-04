package com.team27.amazon.order.service;

import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.repository.OrderItemRepository;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceCachingTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRedisCacheService orderRedisCacheService;

    @InjectMocks
    private OrderItemService orderItemService;

    @Test
    void getOrderItemByIdCacheHitSkipsRepository() {
        OrderItem cachedItem = new OrderItem();
        cachedItem.setId(99L);
        cachedItem.setProductId(123L);
        cachedItem.setQuantity(2);
        cachedItem.setPriceAtPurchase(49.99);
        cachedItem.setItemOrder(1);
        cachedItem.setMetadata(Map.of("color", "black"));

        when(orderRedisCacheService.orderItemKey(99L)).thenReturn("order-service::order-item::99");
        doReturn(Optional.of(OrderItemService.OrderItemCacheSnapshot.from(cachedItem)))
            .when(orderRedisCacheService)
            .get(eq("order-service::order-item::99"), org.mockito.ArgumentMatchers.any(Class.class));

        Optional<OrderItem> result = orderItemService.getOrderItemById(99L);

        assertTrue(result.isPresent());
        assertEquals(99L, result.orElseThrow().getId());
        verify(orderItemRepository, never()).findById(99L);
    }

    @Test
    void getOrderItemByIdCacheMissCachesForFifteenMinutes() {
        OrderItem dbItem = new OrderItem();
        dbItem.setId(55L);
        dbItem.setProductId(222L);
        dbItem.setQuantity(3);
        dbItem.setPriceAtPurchase(19.99);
        dbItem.setItemOrder(2);
        dbItem.setMetadata(new HashMap<>(Map.of("size", "M")));

        when(orderRedisCacheService.orderItemKey(55L)).thenReturn("order-service::order-item::55");
        doReturn(Optional.empty())
            .when(orderRedisCacheService)
            .get(eq("order-service::order-item::55"), org.mockito.ArgumentMatchers.any(Class.class));
        when(orderItemRepository.findById(55L)).thenReturn(Optional.of(dbItem));

        Optional<OrderItem> result = orderItemService.getOrderItemById(55L);

        assertTrue(result.isPresent());
        verify(orderRedisCacheService).set(eq("order-service::order-item::55"), any(), eq(Duration.ofMinutes(15)));
        }

        @Test
        void getOrderItemByIdRedisFailuresStillReturnDbResult() {
        OrderItem dbItem = new OrderItem();
        dbItem.setId(77L);
        dbItem.setProductId(333L);
        dbItem.setQuantity(1);
        dbItem.setPriceAtPurchase(9.99);
        dbItem.setItemOrder(1);
        dbItem.setMetadata(Map.of("gift", true));

        when(orderRedisCacheService.orderItemKey(77L)).thenReturn("order-service::order-item::77");
        doThrow(new RuntimeException("redis get failed"))
            .when(orderRedisCacheService)
            .get(eq("order-service::order-item::77"), org.mockito.ArgumentMatchers.any(Class.class));
        when(orderItemRepository.findById(77L)).thenReturn(Optional.of(dbItem));

        Logger logger = (Logger) LoggerFactory.getLogger(OrderItemService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Optional<OrderItem> result = orderItemService.getOrderItemById(77L);

            assertTrue(result.isPresent());
            assertEquals(77L, result.orElseThrow().getId());
            assertTrue(appender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("Redis get failed for key order-service::order-item::77")));
        } finally {
            logger.detachAppender(appender);
        }

        OrderItem dbItem2 = new OrderItem();
        dbItem2.setId(78L);
        dbItem2.setProductId(444L);
        dbItem2.setQuantity(2);
        dbItem2.setPriceAtPurchase(29.99);
        dbItem2.setItemOrder(1);
        dbItem2.setMetadata(Map.of("gift", false));

        when(orderRedisCacheService.orderItemKey(78L)).thenReturn("order-service::order-item::78");
        doReturn(Optional.empty())
            .when(orderRedisCacheService)
            .get(eq("order-service::order-item::78"), org.mockito.ArgumentMatchers.any(Class.class));
        when(orderItemRepository.findById(78L)).thenReturn(Optional.of(dbItem2));
        doThrow(new RuntimeException("redis set failed"))
            .when(orderRedisCacheService)
            .set(eq("order-service::order-item::78"), any(), eq(Duration.ofMinutes(15)));

        Logger loggerForSet = (Logger) LoggerFactory.getLogger(OrderItemService.class);
        ListAppender<ILoggingEvent> setAppender = new ListAppender<>();
        setAppender.start();
        loggerForSet.addAppender(setAppender);
        try {
            Optional<OrderItem> result = orderItemService.getOrderItemById(78L);

            assertTrue(result.isPresent());
            assertEquals(78L, result.orElseThrow().getId());
            assertTrue(setAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("Redis set failed for key order-service::order-item::78")));
        } finally {
            loggerForSet.detachAppender(setAppender);
        }
    }

    @Test
    void getAllOrderItemsRemainsUncached() {
        when(orderItemRepository.findAll()).thenReturn(List.of(new OrderItem()));

        List<OrderItem> result = orderItemService.getAllOrderItems();

        assertEquals(1, result.size());
        verifyNoInteractions(orderRedisCacheService);
    }

    @Test
    void updateOrderItemInvalidatesExpectedCaches() {
        Order parentOrder = new Order();
        parentOrder.setId(500L);

        OrderItem existing = new OrderItem();
        existing.setId(200L);
        existing.setProductId(99L);
        existing.setOrder(parentOrder);

        OrderItem updated = new OrderItem();
        updated.setProductId(100L);
        updated.setQuantity(2);

        when(orderItemRepository.findById(200L)).thenReturn(Optional.of(existing));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRedisCacheService.orderItemKey(200L)).thenReturn("order-service::order-item::200");
        when(orderRedisCacheService.orderKey(500L)).thenReturn("order-service::order::500");

        Optional<OrderItem> result = orderItemService.updateOrderItem(200L, updated);

        assertTrue(result.isPresent());
        verify(orderRedisCacheService).delete("order-service::order-item::200");
        verify(orderRedisCacheService).delete("order-service::order::500");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F3::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F6::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F9::*");
        verify(orderRedisCacheService).deleteByPattern("order-service::S3-F10::*");
        verify(orderRedisCacheService).deleteByPattern("product-service::S2-F12::*");
    }
}
