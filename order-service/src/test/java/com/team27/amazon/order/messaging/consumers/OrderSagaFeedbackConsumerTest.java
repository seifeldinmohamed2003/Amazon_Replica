package com.team27.amazon.order.messaging.consumers;

import com.team27.amazon.contracts.events.ShipmentCreatedEvent;
import com.team27.amazon.contracts.events.ShipmentStatusChangedEvent;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for OrderSagaFeedbackConsumer (shipment events).
 */
@ExtendWith(MockitoExtension.class)
class OrderSagaFeedbackConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderSagaFeedbackConsumer consumer;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(50L);
        order.setUserId(100L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setMetadata(new HashMap<>());
    }

    // ========== shipment.created Tests ==========

    @Test
    void onShipmentCreatedStoresShipmentIdInMetadata() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(123L, 50L, "FedEx", "TRACK123");
        consumer.onShipmentCreated(event);

        verify(orderRepository).findById(50L);
        verify(orderRepository).save(any(Order.class));
        assertTrue(order.getMetadata().containsKey("shipmentId"));
        assertEquals(123L, order.getMetadata().get("shipmentId"));
    }

    @Test
    void onShipmentCreatedIsIdempotent() {
        // Already stored the same shipmentId
        order.getMetadata().put("shipmentId", 123L);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(123L, 50L, "FedEx", "TRACK123");
        consumer.onShipmentCreated(event);

        verify(orderRepository).findById(50L);
        // Should not save if already present with same value
        verify(orderRepository, never()).save(any());
    }

    @Test
    void onShipmentCreatedUpdatesIfDifferentShipmentId() {
        // Already has a different shipmentId
        order.getMetadata().put("shipmentId", 999L);
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(123L, 50L, "FedEx", "TRACK123");
        consumer.onShipmentCreated(event);

        verify(orderRepository).findById(50L);
        verify(orderRepository).save(any(Order.class));
        assertEquals(123L, order.getMetadata().get("shipmentId"));
    }

    @Test
    void onShipmentCreatedHandlesOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ShipmentCreatedEvent event = new ShipmentCreatedEvent(123L, 999L, "FedEx", "TRACK123");
        // Should not throw, logs warning and returns
        assertDoesNotThrow(() -> consumer.onShipmentCreated(event));

        verify(orderRepository).findById(999L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void onShipmentCreatedHandlesNullEvent() {
        // Should not throw on null event
        assertDoesNotThrow(() -> consumer.onShipmentCreated(null));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void onShipmentCreatedHandlesNullOrderId() {
        ShipmentCreatedEvent event = new ShipmentCreatedEvent(123L, null, "FedEx", "TRACK123");
        assertDoesNotThrow(() -> consumer.onShipmentCreated(event));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void onShipmentCreatedHandlesNullShipmentId() {
        ShipmentCreatedEvent event = new ShipmentCreatedEvent(null, 50L, "FedEx", "TRACK123");
        assertDoesNotThrow(() -> consumer.onShipmentCreated(event));
        verify(orderRepository, never()).findById(any());
    }

    // ========== shipment.status-changed Tests ==========

    @Test
    void onShipmentStatusChangedStoresStatusInMetadata() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, 50L, "IN_TRANSIT");
        consumer.onShipmentStatusChanged(event);

        verify(orderRepository).findById(50L);
        verify(orderRepository).save(any(Order.class));
        assertTrue(order.getMetadata().containsKey("shipmentStatus"));
        assertEquals("IN_TRANSIT", order.getMetadata().get("shipmentStatus"));
    }

    @Test
    void onShipmentStatusChangedStoresShipmentIdIfProvided() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(456L, 50L, "IN_TRANSIT");
        consumer.onShipmentStatusChanged(event);

        verify(orderRepository).save(any(Order.class));
        assertEquals("IN_TRANSIT", order.getMetadata().get("shipmentStatus"));
        assertEquals(456L, order.getMetadata().get("shipmentId"));
    }

    @Test
    void onShipmentStatusChangedIsIdempotent() {
        // Already stored the same status
        order.getMetadata().put("shipmentStatus", "IN_TRANSIT");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, 50L, "IN_TRANSIT");
        consumer.onShipmentStatusChanged(event);

        verify(orderRepository).findById(50L);
        // Should not save if already same status
        verify(orderRepository, never()).save(any());
    }

    @Test
    void onShipmentStatusChangedUpdatesIfDifferentStatus() {
        // Already has a different status
        order.getMetadata().put("shipmentStatus", "PENDING");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, 50L, "DELIVERED");
        consumer.onShipmentStatusChanged(event);

        verify(orderRepository).save(any(Order.class));
        assertEquals("DELIVERED", order.getMetadata().get("shipmentStatus"));
    }

    @Test
    void onShipmentStatusChangedHandlesOrderNotFound() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, 999L, "IN_TRANSIT");
        assertDoesNotThrow(() -> consumer.onShipmentStatusChanged(event));

        verify(orderRepository).findById(999L);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void onShipmentStatusChangedHandlesNullEvent() {
        assertDoesNotThrow(() -> consumer.onShipmentStatusChanged(null));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void onShipmentStatusChangedHandlesNullOrderId() {
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, null, "IN_TRANSIT");
        assertDoesNotThrow(() -> consumer.onShipmentStatusChanged(event));
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void onShipmentStatusChangedHandlesNullStatus() {
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(123L, 50L, null);
        assertDoesNotThrow(() -> consumer.onShipmentStatusChanged(event));
        verify(orderRepository, never()).findById(any());
    }
}
