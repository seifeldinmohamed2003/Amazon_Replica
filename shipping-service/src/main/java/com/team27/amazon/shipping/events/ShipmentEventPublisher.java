package com.team27.amazon.shipping.events;

import com.team27.amazon.contracts.events.ShipmentCancelledEvent;
import com.team27.amazon.contracts.events.ShipmentCreatedEvent;
import com.team27.amazon.contracts.events.ShipmentStatusChangedEvent;
import com.team27.amazon.shipping.config.ShippingRabbitMQConfig;
import com.team27.amazon.shipping.model.Shipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ShipmentEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ShipmentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishShipmentCreated(Shipment shipment) {
        ShipmentCreatedEvent event = new ShipmentCreatedEvent(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getCarrier(),
                shipment.getTrackingNumber()
        );

        rabbitTemplate.convertAndSend(
                ShippingRabbitMQConfig.SHIPMENT_EVENTS_EXCHANGE,
                ShippingRabbitMQConfig.SHIPMENT_CREATED_ROUTING_KEY,
                event
        );

        log.info("Published shipment.created for shipmentId={} orderId={}",
                shipment.getId(),
                shipment.getOrderId());
    }

    public void publishShipmentStatusChanged(Shipment shipment) {
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getStatus() == null ? null : shipment.getStatus().name()
        );

        rabbitTemplate.convertAndSend(
                ShippingRabbitMQConfig.SHIPMENT_EVENTS_EXCHANGE,
                ShippingRabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY,
                event
        );

        log.info("Published shipment.status-changed for shipmentId={} orderId={} status={}",
                shipment.getId(),
                shipment.getOrderId(),
                shipment.getStatus());
    }

    public void publishShipmentCancelled(Shipment shipment) {
        ShipmentCancelledEvent event = new ShipmentCancelledEvent(
                shipment.getId(),
                shipment.getOrderId()
        );

        rabbitTemplate.convertAndSend(
                ShippingRabbitMQConfig.SHIPMENT_EVENTS_EXCHANGE,
                ShippingRabbitMQConfig.SHIPMENT_CANCELLED_ROUTING_KEY,
                event
        );

        log.info("Published shipment.cancelled for shipmentId={} orderId={}",
                shipment.getId(),
                shipment.getOrderId());
    }
}