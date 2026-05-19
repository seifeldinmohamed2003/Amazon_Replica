package com.team27.amazon.order.messaging.consumers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.events.PaymentCompletedEvent;
import com.team27.amazon.contracts.events.PaymentFailedEvent;
import com.team27.amazon.contracts.events.PaymentInitiatedEvent;
import com.team27.amazon.contracts.events.PaymentRefundedEvent;
import com.team27.amazon.contracts.events.ShipmentCreatedEvent;
import com.team27.amazon.contracts.events.ShipmentStatusChangedEvent;
import com.team27.amazon.order.config.OrderEventConfig;
import com.team27.amazon.order.messaging.publishers.OrderEventPublisher;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import com.team27.amazon.order.model.OrderStatus;
import com.team27.amazon.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * S3-EVENTS consumer for order-service saga feedback.
 *
 * A single queue listener dispatches by received routing key so the queue can carry
 * shipment and payment events safely through the same converter setup.
 */
@Component
public class OrderSagaFeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaFeedbackConsumer.class);

        private final OrderRepository orderRepository;
        private final OrderEventPublisher orderEventPublisher;
        private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderSagaFeedbackConsumer(
            OrderRepository orderRepository,
            OrderEventPublisher orderEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @RabbitListener(queues = OrderEventConfig.ORDER_SAGA_FEEDBACK_QUEUE)
    public void onSagaFeedback(Message message) {
        try {
            if (message == null || message.getMessageProperties() == null) {
                log.warn("Invalid saga feedback message received: {}", message);
                return;
            }

            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            if (routingKey == null || routingKey.isBlank()) {
                log.warn("Saga feedback message missing routing key: {}", message);
                return;
            }

            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.warn("Saga feedback message missing body for routing key {}", routingKey);
                return;
            }

            String json = new String(body, StandardCharsets.UTF_8);

            switch (routingKey) {
                case EventRoutingKeys.SHIPMENT_CREATED -> handleShipmentCreated(read(json, ShipmentCreatedEvent.class));
                case EventRoutingKeys.SHIPMENT_STATUS_CHANGED -> handleShipmentStatusChanged(read(json, ShipmentStatusChangedEvent.class));
                case EventRoutingKeys.PAYMENT_INITIATED -> handlePaymentInitiated(read(json, PaymentInitiatedEvent.class));
                case EventRoutingKeys.PAYMENT_COMPLETED -> handlePaymentCompleted(read(json, PaymentCompletedEvent.class));
                case EventRoutingKeys.PAYMENT_FAILED -> handlePaymentFailed(read(json, PaymentFailedEvent.class));
                case EventRoutingKeys.PAYMENT_REFUNDED -> handlePaymentRefunded(read(json, PaymentRefundedEvent.class));
                default -> log.warn("Ignoring unsupported saga feedback routing key {}", routingKey);
            }
        } catch (RuntimeException exception) {
            log.error("Unexpected error while processing saga feedback message", exception);
            throw exception;
        }
    }

    private void handleShipmentCreated(ShipmentCreatedEvent event) {
        if (event == null || event.orderId() == null || event.shipmentId() == null) {
            log.warn("Invalid shipment.created event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for shipment.created event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        ensureMetadata(order);

        Object existingShipmentId = order.getMetadata().get("shipmentId");
        if (event.shipmentId().equals(existingShipmentId)) {
            log.debug("Shipment.created already processed for orderId={}, shipmentId={}", event.orderId(), event.shipmentId());
            return;
        }

        order.getMetadata().put("shipmentId", event.shipmentId());
        orderRepository.save(order);

        log.info("Processed shipment.created for orderId={} shipmentId={}", event.orderId(), event.shipmentId());
    }

    private void handleShipmentStatusChanged(ShipmentStatusChangedEvent event) {
        if (event == null || event.orderId() == null || event.newStatus() == null) {
            log.warn("Invalid shipment.status-changed event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for shipment.status-changed event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        ensureMetadata(order);

        Object existingStatus = order.getMetadata().get("shipmentStatus");
        if (event.newStatus().equals(existingStatus)) {
            log.debug("Shipment.status-changed already processed for orderId={}, status={}", event.orderId(), event.newStatus());
            return;
        }

        order.getMetadata().put("shipmentStatus", event.newStatus());
        if (event.shipmentId() != null) {
            order.getMetadata().put("shipmentId", event.shipmentId());
        }

        orderRepository.save(order);

        log.info("Processed shipment.status-changed for orderId={} status={}", event.orderId(), event.newStatus());
    }

    private void handlePaymentInitiated(PaymentInitiatedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Invalid payment.initiated event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for payment.initiated event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        if (isTerminalPaymentStatus(order.getStatus())) {
            log.debug("Ignoring payment.initiated for orderId={} because status is already {}", event.orderId(), order.getStatus());
            return;
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Ignoring payment.initiated for orderId={} in status {}", event.orderId(), order.getStatus());
            return;
        }

        ensureMetadata(order);
        storeTransactionId(order, event.transactionId());
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);

        log.info("Processed payment.initiated for orderId={} transactionId={} -> PAYMENT_PENDING", event.orderId(), event.transactionId());
    }

    private void handlePaymentCompleted(PaymentCompletedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Invalid payment.completed event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for payment.completed event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PAID) {
            log.debug("Ignoring payment.completed for orderId={} because status is already PAID", event.orderId());
            return;
        }

        if (order.getStatus() == OrderStatus.PAYMENT_FAILED
                || order.getStatus() == OrderStatus.REFUNDED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.RETURNED) {
            log.warn("Ignoring payment.completed for orderId={} in terminal status {}", event.orderId(), order.getStatus());
            return;
        }

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Ignoring payment.completed for orderId={} in status {}", event.orderId(), order.getStatus());
            return;
        }

        ensureMetadata(order);
        storeTransactionId(order, event.transactionId());
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        log.info("Processed payment.completed for orderId={} transactionId={} -> PAID", event.orderId(), event.transactionId());
    }

    private void handlePaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Invalid payment.failed event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for payment.failed event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED
                || order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.REFUNDED) {
            log.debug("Ignoring payment.failed for orderId={} because status is already {}", event.orderId(), order.getStatus());
            return;
        }

        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Ignoring payment.failed for orderId={} in status {}", event.orderId(), order.getStatus());
            return;
        }

        ensureMetadata(order);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> restoredItems = savedOrder.getOrderItems() == null
                ? List.of()
                : new ArrayList<>(savedOrder.getOrderItems());
        orderEventPublisher.publishOrderCancelled(savedOrder, restoredItems, "payment_failed");

        log.info("Processed payment.failed for orderId={} -> PAYMENT_FAILED and published order.cancelled compensation", event.orderId());
    }

    private void handlePaymentRefunded(PaymentRefundedEvent event) {
        if (event == null || event.orderId() == null) {
            log.warn("Invalid payment.refunded event received: {}", event);
            return;
        }

        Optional<Order> orderOpt = orderRepository.findById(event.orderId());
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for payment.refunded event: orderId={}", event.orderId());
            return;
        }

        Order order = orderOpt.get();
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.debug("Ignoring payment.refunded for orderId={} because status is already REFUNDED", event.orderId());
            return;
        }

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED
                && order.getStatus() != OrderStatus.CANCELLED
                && order.getStatus() != OrderStatus.PAID) {
            log.warn("Ignoring payment.refunded for orderId={} in status {}", event.orderId(), order.getStatus());
            return;
        }

        ensureMetadata(order);
        storeTransactionId(order, event.transactionId());
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        log.info("Processed payment.refunded for orderId={} transactionId={} -> REFUNDED", event.orderId(), event.transactionId());
    }

    private void ensureMetadata(Order order) {
        if (order.getMetadata() == null) {
            order.setMetadata(new java.util.HashMap<>());
        }
    }

    private void storeTransactionId(Order order, Long transactionId) {
        if (transactionId != null) {
            order.getMetadata().put("transactionId", transactionId);
        }
    }

    private boolean isTerminalPaymentStatus(OrderStatus status) {
        return status == OrderStatus.PAYMENT_PENDING
                || status == OrderStatus.PAID
                || status == OrderStatus.PAYMENT_FAILED
                || status == OrderStatus.REFUNDED;
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize saga feedback event to " + type.getSimpleName(), exception);
        }
    }
}
