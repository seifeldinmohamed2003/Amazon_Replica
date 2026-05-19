package com.team27.amazon.order.messaging.consumers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.events.PaymentCompletedEvent;
import com.team27.amazon.contracts.events.PaymentFailedEvent;
import com.team27.amazon.contracts.events.PaymentInitiatedEvent;
import com.team27.amazon.contracts.events.PaymentRefundedEvent;
import com.team27.amazon.contracts.events.ShipmentCreatedEvent;
import com.team27.amazon.contracts.events.ShipmentStatusChangedEvent;
import com.team27.amazon.order.messaging.publishers.OrderEventPublisher;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class OrderSagaFeedbackConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private ObjectMapper objectMapper;
    private OrderSagaFeedbackConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new OrderSagaFeedbackConsumer(orderRepository, orderEventPublisher);
    }

    @Test
    void shipmentCreatedStoresShipmentIdInMetadata() {
        Order order = order(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new ShipmentCreatedEvent(50L, 1L, "DHL", "TRACK-50"), EventRoutingKeys.SHIPMENT_CREATED));

        assertEquals(50L, order.getMetadata().get("shipmentId"));
        verify(orderRepository).save(order);
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void shipmentStatusChangedStoresStatusAndShipmentId() {
        Order order = order(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new ShipmentStatusChangedEvent(50L, 1L, "SHIPPED"), EventRoutingKeys.SHIPMENT_STATUS_CHANGED));

        assertEquals("SHIPPED", order.getMetadata().get("shipmentStatus"));
        assertEquals(50L, order.getMetadata().get("shipmentId"));
        verify(orderRepository).save(order);
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void paymentInitiatedMovesDeliveredOrderToPaymentPending() {
        Order order = order(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new PaymentInitiatedEvent(90L, 1L, 77L, 125.0), EventRoutingKeys.PAYMENT_INITIATED));

        assertEquals(OrderStatus.PAYMENT_PENDING, order.getStatus());
        assertEquals(90L, order.getMetadata().get("transactionId"));
        verify(orderRepository).save(order);
    }

    @Test
    void paymentInitiatedIsIdempotentWhenOrderAlreadyPaymentPending() {
        Order order = order(1L, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        consumer.onSagaFeedback(message(new PaymentInitiatedEvent(90L, 1L, 77L, 125.0), EventRoutingKeys.PAYMENT_INITIATED));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void paymentCompletedMovesPaymentPendingOrderToPaid() {
        Order order = order(1L, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new PaymentCompletedEvent(90L, 1L, 77L, 125.0), EventRoutingKeys.PAYMENT_COMPLETED));

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(90L, order.getMetadata().get("transactionId"));
        verify(orderRepository).save(order);
    }

    @Test
    void paymentCompletedIsIdempotentWhenAlreadyPaid() {
        Order order = order(1L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        consumer.onSagaFeedback(message(new PaymentCompletedEvent(90L, 1L, 77L, 125.0), EventRoutingKeys.PAYMENT_COMPLETED));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void paymentCompletedAllowsDeliveredToPaidOutOfOrder() {
        Order order = order(1L, OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new PaymentCompletedEvent(90L, 1L, 77L, 125.0), EventRoutingKeys.PAYMENT_COMPLETED));

        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void paymentFailedMovesPaymentPendingOrderToPaymentFailedAndPublishesCancellation() {
        Order order = order(1L, OrderStatus.PAYMENT_PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new PaymentFailedEvent(90L, 1L, "card_declined"), EventRoutingKeys.PAYMENT_FAILED));

        assertEquals(OrderStatus.PAYMENT_FAILED, order.getStatus());
        verify(orderRepository).save(order);
        verify(orderEventPublisher).publishOrderCancelled(eq(order), anyList(), eq("payment_failed"));
    }

    @Test
    void paymentFailedIsIdempotentWhenAlreadyPaymentFailed() {
        Order order = order(1L, OrderStatus.PAYMENT_FAILED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        consumer.onSagaFeedback(message(new PaymentFailedEvent(90L, 1L, "card_declined"), EventRoutingKeys.PAYMENT_FAILED));

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderCancelled(any(), anyList(), any());
    }

    @Test
    void paymentRefundedMovesPaymentFailedOrderToRefunded() {
        Order order = order(1L, OrderStatus.PAYMENT_FAILED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        consumer.onSagaFeedback(message(new PaymentRefundedEvent(90L, 1L, 125.0), EventRoutingKeys.PAYMENT_REFUNDED));

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(90L, order.getMetadata().get("transactionId"));
        verify(orderRepository).save(order);
    }

    @Test
    void paymentRefundedIsIdempotentWhenAlreadyRefunded() {
        Order order = order(1L, OrderStatus.REFUNDED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        consumer.onSagaFeedback(message(new PaymentRefundedEvent(90L, 1L, 125.0), EventRoutingKeys.PAYMENT_REFUNDED));

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void missingOrderReturnsNormallyForPaymentEvents() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentInitiatedEvent(90L, 999L, 77L, 125.0), EventRoutingKeys.PAYMENT_INITIATED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentCompletedEvent(90L, 999L, 77L, 125.0), EventRoutingKeys.PAYMENT_COMPLETED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentFailedEvent(90L, 999L, "card_declined"), EventRoutingKeys.PAYMENT_FAILED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentRefundedEvent(90L, 999L, 125.0), EventRoutingKeys.PAYMENT_REFUNDED)));

        verifyNoInteractions(orderEventPublisher);
    }

    @Test
    void invalidNullEventDataReturnsNormally() {
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentInitiatedEvent(90L, null, 77L, 125.0), EventRoutingKeys.PAYMENT_INITIATED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentCompletedEvent(90L, null, 77L, 125.0), EventRoutingKeys.PAYMENT_COMPLETED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentFailedEvent(90L, null, "card_declined"), EventRoutingKeys.PAYMENT_FAILED)));
        assertDoesNotThrow(() -> consumer.onSagaFeedback(message(new PaymentRefundedEvent(90L, null, 125.0), EventRoutingKeys.PAYMENT_REFUNDED)));

        verifyNoInteractions(orderEventPublisher);
    }

    private Message message(Object event, String routingKey) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setReceivedRoutingKey(routingKey);
            return new Message(objectMapper.writeValueAsBytes(event), properties);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Order order(Long id, OrderStatus status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(77L);
        order.setStatus(status);
        order.setMetadata(new HashMap<>());

        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setProductId(101L);
        item1.setQuantity(2);
        item1.setPriceAtPurchase(50.0);
        item1.setItemOrder(1);

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setProductId(102L);
        item2.setQuantity(1);
        item2.setPriceAtPurchase(25.0);
        item2.setItemOrder(2);

        order.setOrderItems(new ArrayList<>(List.of(item1, item2)));
        return order;
    }
}
