package com.team27.amazon.shipping.messaging;

import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.team27.amazon.shipping.config.rabbit.RabbitMQConfig;
import com.team27.amazon.shipping.dto.event.OrderEvent;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;

@Component
public class OrderEventConsumer {

    private final ShipmentRepository shipmentRepository;

    public OrderEventConsumer(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @RabbitListener(
            queues = RabbitMQConfig.ORDER_SAGA_QUEUE
    )
    public void consumeOrderEvent(OrderEvent event) {

        if (event == null ||
            event.getOrderId() == null ||
            event.getEventType() == null) {
            return;
        }

        List<Shipment> shipments =
                shipmentRepository.findByOrderId(event.getOrderId());

        ShipmentStatus targetStatus =
                resolveStatusFromEvent(event.getEventType());

        if (targetStatus == null) {
            return;
        }

        for (Shipment shipment : shipments) {

            if (shipment.getStatus() == ShipmentStatus.DELIVERED ||
                shipment.getStatus() == ShipmentStatus.RETURNED) {
                continue;
            }

            shipment.setStatus(targetStatus);

            shipmentRepository.save(shipment);
        }
    }

    private ShipmentStatus resolveStatusFromEvent(String eventType) {

        return switch (eventType) {

            case RabbitMQConfig.ORDER_COMPLETED_ROUTING_KEY ->
                    ShipmentStatus.DELIVERED;

            case RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY ->
                    ShipmentStatus.RETURNED;

            default -> null;
        };
    }
}
