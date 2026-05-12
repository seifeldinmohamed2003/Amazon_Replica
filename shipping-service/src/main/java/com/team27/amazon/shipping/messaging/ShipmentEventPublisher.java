package com.team27.amazon.shipping.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.team27.amazon.shipping.config.rabbit.RabbitMQConfig;
import com.team27.amazon.shipping.dto.event.ShipmentEvent;

@Component
public class ShipmentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ShipmentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishShipmentCreated(ShipmentEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SHIPMENT_EXCHANGE,
                RabbitMQConfig.SHIPMENT_CREATED_ROUTING_KEY,
                event
        );
    }

    public void publishShipmentStatusChanged(ShipmentEvent event) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SHIPMENT_EXCHANGE,
                RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY,
                event
        );
    }
}
