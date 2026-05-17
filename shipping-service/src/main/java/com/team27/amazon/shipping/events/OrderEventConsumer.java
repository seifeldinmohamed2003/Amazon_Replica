package com.team27.amazon.shipping.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.contracts.events.OrderCancelledEvent;
import com.team27.amazon.contracts.events.OrderCompletedEvent;
import com.team27.amazon.contracts.events.OrderPlacedEvent;
import com.team27.amazon.shipping.config.RedisConfiguration;
import com.team27.amazon.shipping.config.ShippingRabbitMQConfig;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private static final List<ShipmentStatus> ACTIVE_STATUSES = List.of(
            ShipmentStatus.PROCESSING,
            ShipmentStatus.SHIPPED,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventPublisher shipmentEventPublisher;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(
            ShipmentRepository shipmentRepository,
            ShipmentEventPublisher shipmentEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentEventPublisher = shipmentEventPublisher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = ShippingRabbitMQConfig.SHIPMENT_SAGA_QUEUE)
    @Transactional
    @CacheEvict(cacheNames = {
            RedisConfiguration.CACHE_SHIPMENT_DETAIL,
            RedisConfiguration.CACHE_S4_F1,
            RedisConfiguration.CACHE_S4_F3,
            RedisConfiguration.CACHE_S4_F6,
            RedisConfiguration.CACHE_S4_F8,
            RedisConfiguration.CACHE_S4_F9,
            RedisConfiguration.CACHE_S4_F10
    }, allEntries = true)
    public void handleOrderEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        try {
            switch (routingKey) {
                case ShippingRabbitMQConfig.ORDER_PLACED_ROUTING_KEY -> {
                    OrderPlacedEvent event = objectMapper.readValue(message.getBody(), OrderPlacedEvent.class);
                    handleOrderPlaced(event);
                }

                case ShippingRabbitMQConfig.ORDER_COMPLETED_ROUTING_KEY -> {
                    OrderCompletedEvent event = objectMapper.readValue(message.getBody(), OrderCompletedEvent.class);
                    handleOrderCompleted(event);
                }

                case ShippingRabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY -> {
                    OrderCancelledEvent event = objectMapper.readValue(message.getBody(), OrderCancelledEvent.class);
                    handleOrderCancelled(event);
                }

                default -> log.warn("Ignored unsupported order event routingKey={}", routingKey);
            }
        } catch (Exception exception) {
            log.error("Failed to consume order event routingKey={}", routingKey, exception);
            throw new IllegalStateException("Failed to consume order event: " + routingKey, exception);
        }
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        log.info(
                "Consumed order.placed for orderId={} userId={} shippingAddressId={}; slot prep only, no shipment created",
                event.orderId(),
                event.userId(),
                event.shippingAddressId()
        );

        // M3 requirement: order.placed is only slot prep/logging in shipping-service.
        // No Shipment row is created here.
    }

    private void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Consumed order.completed for orderId={}", event.orderId());

        Shipment shipment = shipmentRepository
                .findFirstByOrderIdAndStatusInOrderByLastUpdateDesc(event.orderId(), ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalStateException(
                        "No active shipment found for completed order " + event.orderId()
                ));

        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setActualDelivery(LocalDate.now());
        shipment.setLastUpdate(LocalDateTime.now());

        Shipment savedShipment = shipmentRepository.save(shipment);
        shipmentEventPublisher.publishShipmentStatusChanged(savedShipment);

        log.info(
                "Marked shipment DELIVERED from order.completed shipmentId={} orderId={}",
                savedShipment.getId(),
                savedShipment.getOrderId()
        );
    }

    private void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Consumed order.cancelled for orderId={} reason={}", event.orderId(), event.reason());

        shipmentRepository
                .findFirstByOrderIdAndStatusInOrderByLastUpdateDesc(event.orderId(), ACTIVE_STATUSES)
                .ifPresentOrElse(
                        shipment -> {
                            shipment.setStatus(ShipmentStatus.CANCELLED);
                            shipment.setLastUpdate(LocalDateTime.now());

                            Shipment savedShipment = shipmentRepository.save(shipment);
                            shipmentEventPublisher.publishShipmentCancelled(savedShipment);

                            log.info(
                                    "Marked shipment CANCELLED from order.cancelled shipmentId={} orderId={}",
                                    savedShipment.getId(),
                                    savedShipment.getOrderId()
                            );
                        },
                        () -> log.warn("No active shipment found to cancel for orderId={}", event.orderId())
                );
    }
}