package com.team27.amazon.order.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.team27.amazon.order.config.rabbit.RabbitMQConfig;
import com.team27.amazon.order.dto.event.OrderEvent;

@Component
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishOrderCompleted(Long orderId) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SHIPPING_EXCHANGE,
                RabbitMQConfig.ORDER_COMPLETED_ROUTING_KEY,
                new OrderEvent(orderId, "order.completed")
        );
    }

    public void publishOrderCancelled(Long orderId) {

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SHIPPING_EXCHANGE,
                RabbitMQConfig.ORDER_CANCELLED_ROUTING_KEY,
                new OrderEvent(orderId, "order.cancelled")
        );
    }
}
