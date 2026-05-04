package com.team27.amazon.order.service;

import com.team27.amazon.order.cache.OrderRedisCacheService;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.repository.OrderItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        when(orderRedisCacheService.orderItemKey(99L)).thenReturn("order-service::order-item::99");
        when(orderRedisCacheService.get("order-service::order-item::99", OrderItem.class)).thenReturn(Optional.of(cachedItem));

        Optional<OrderItem> result = orderItemService.getOrderItemById(99L);

        assertTrue(result.isPresent());
        assertEquals(99L, result.orElseThrow().getId());
        verify(orderItemRepository, never()).findById(99L);
    }

    @Test
    void getOrderItemByIdCacheMissCachesForFifteenMinutes() {
        OrderItem dbItem = new OrderItem();
        dbItem.setId(55L);

        when(orderRedisCacheService.orderItemKey(55L)).thenReturn("order-service::order-item::55");
        when(orderRedisCacheService.get("order-service::order-item::55", OrderItem.class)).thenReturn(Optional.empty());
        when(orderItemRepository.findById(55L)).thenReturn(Optional.of(dbItem));

        Optional<OrderItem> result = orderItemService.getOrderItemById(55L);

        assertTrue(result.isPresent());
        verify(orderRedisCacheService).set("order-service::order-item::55", dbItem, Duration.ofMinutes(15));
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
