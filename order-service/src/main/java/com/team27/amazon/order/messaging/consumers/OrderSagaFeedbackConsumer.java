package com.team27.amazon.order.messaging.consumers;

import com.team27.amazon.contracts.events.ShipmentCreatedEvent;
import com.team27.amazon.contracts.events.ShipmentStatusChangedEvent;
import com.team27.amazon.order.config.OrderEventConfig;
import com.team27.amazon.order.model.Order;
import com.team27.amazon.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * S3-EVENTS Consumer for order-service saga feedback.
 * 
 * Listens on order.saga-feedback queue for:
 * - shipment.created
 * - shipment.status-changed
 * 
 * (Payment events deferred until PAYMENT_PENDING/PAID/PAYMENT_FAILED/REFUNDED are added to OrderStatus enum)
 */
@Component
public class OrderSagaFeedbackConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaFeedbackConsumer.class);

    private final OrderRepository orderRepository;

    public OrderSagaFeedbackConsumer(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Consumes shipment.created event.
     * Stores shipmentId on the order (in metadata if no dedicated field).
     * Idempotent: repeated same shipmentId is safe.
     * 
     * @param event the shipment.created event
     */
    @RabbitListener(queues = OrderEventConfig.ORDER_SAGA_FEEDBACK_QUEUE)
    public void onShipmentCreated(ShipmentCreatedEvent event) {
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
        
        // Store shipmentId in metadata (using key "shipmentId")
        Object existingShipmentId = order.getMetadata().get("shipmentId");
        if (existingShipmentId != null && existingShipmentId.equals(event.shipmentId())) {
            // Already stored, idempotent
            log.debug("Shipment.created already processed for orderId={}, shipmentId={}", 
                    event.orderId(), event.shipmentId());
            return;
        }

        // Store or update shipmentId
        order.getMetadata().put("shipmentId", event.shipmentId());
        orderRepository.save(order);
        
        log.info("Processed shipment.created for orderId={} shipmentId={}", 
                event.orderId(), event.shipmentId());
    }

    /**
     * Consumes shipment.status-changed event.
     * Stores shipmentStatus on the order (in metadata if no dedicated field).
     * Idempotent: repeated same status is safe.
     * 
     * @param event the shipment.status-changed event
     */
    @RabbitListener(queues = OrderEventConfig.ORDER_SAGA_FEEDBACK_QUEUE)
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) {
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
        
        // Store shipmentStatus in metadata (using key "shipmentStatus")
        Object existingStatus = order.getMetadata().get("shipmentStatus");
        if (existingStatus != null && existingStatus.equals(event.newStatus())) {
            // Already same status, idempotent
            log.debug("Shipment.status-changed already processed for orderId={}, status={}", 
                    event.orderId(), event.newStatus());
            return;
        }

        // Store or update shipmentStatus
        order.getMetadata().put("shipmentStatus", event.newStatus());
        
        // Also store/refresh shipmentId if available
        if (event.shipmentId() != null) {
            order.getMetadata().put("shipmentId", event.shipmentId());
        }

        orderRepository.save(order);
        
        log.info("Processed shipment.status-changed for orderId={} status={}", 
                event.orderId(), event.newStatus());
    }
}
