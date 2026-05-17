package com.team27.amazon.order.messaging.publishers;

import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.events.OrderCancelledEvent;
import com.team27.amazon.contracts.events.OrderCompletedEvent;
import com.team27.amazon.contracts.events.OrderItemPayload;
import com.team27.amazon.contracts.events.OrderPlacedEvent;
import com.team27.amazon.order.config.OrderEventConfig;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * S3-EVENTS Publisher for order-service.
 * 
 * Publishes order events to RabbitMQ:
 * - order.placed
 * - order.completed
 * - order.cancelled
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes an order.placed event when an order is confirmed.
     * 
     * @param order the confirmed order
     */
    public void publishOrderPlaced(Order order) {
        List<OrderItemPayload> items = mapOrderItemsToPayloads(order);
        OrderPlacedEvent event = new OrderPlacedEvent(
                order.getId(),
                order.getUserId(),
                order.getShippingAddressId(),
                items
        );
        rabbitTemplate.convertAndSend(
                OrderEventConfig.ORDER_EVENTS_EXCHANGE,
                EventRoutingKeys.ORDER_PLACED,
                event
        );
        log.info("Published order.placed for orderId={}", order.getId());
    }

    /**
     * Publishes an order.completed event when an order is delivered.
     * 
     * @param order the delivered order
     */
    public void publishOrderCompleted(Order order) {
        OrderCompletedEvent event = new OrderCompletedEvent(
                order.getId(),
                order.getUserId(),
                order.getShippingAddressId(),
                order.getTotalAmount()
        );
        rabbitTemplate.convertAndSend(
                OrderEventConfig.ORDER_EVENTS_EXCHANGE,
                EventRoutingKeys.ORDER_COMPLETED,
                event
        );
        log.info("Published order.completed for orderId={}", order.getId());
    }

    /**
     * Publishes an order.cancelled event when an order is cancelled.
     * 
     * @param order the cancelled order
     * @param restoredItems list of items whose stock was restored
     * @param reason cancellation reason
     */
    public void publishOrderCancelled(Order order, List<OrderItem> restoredItems, String reason) {
        List<OrderItemPayload> items = mapOrderItemsToPayloads(restoredItems);
        OrderCancelledEvent event = new OrderCancelledEvent(
                order.getId(),
                order.getUserId(),
                items,
                reason != null ? reason : "Order cancelled by user"
        );
        rabbitTemplate.convertAndSend(
                OrderEventConfig.ORDER_EVENTS_EXCHANGE,
                EventRoutingKeys.ORDER_CANCELLED,
                event
        );
        log.info("Published order.cancelled for orderId={}", order.getId());
    }

    /**
     * Maps Order items to OrderItemPayload DTOs for event publishing.
     * 
     * @param order the order
     * @return list of OrderItemPayload
     */
    private List<OrderItemPayload> mapOrderItemsToPayloads(Order order) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return List.of();
        }
        return mapOrderItemsToPayloads(order.getOrderItems());
    }

    /**
     * Maps OrderItem list to OrderItemPayload DTOs for event publishing.
     * 
     * @param orderItems the order items
     * @return list of OrderItemPayload
     */
    private List<OrderItemPayload> mapOrderItemsToPayloads(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return List.of();
        }
        return orderItems.stream()
                .map(item -> new OrderItemPayload(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPriceAtPurchase()
                ))
                .collect(Collectors.toList());
    }
}
