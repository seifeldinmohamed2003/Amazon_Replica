package com.team27.amazon.shipping.config.rabbit;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String SHIPMENT_EXCHANGE = "shipment.events";
    public static final String ORDER_EXCHANGE = "order.events";

    public static final String ORDER_SAGA_QUEUE = "shipment.saga-listener";
    public static final String ORDER_SAGA_DLQ = "shipment.saga-listener.dlq";
    public static final String ORDER_SAGA_DLX = "shipment.saga-listener.dlx";

    public static final String ORDER_COMPLETED_ROUTING_KEY = "order.completed";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

    public static final String SHIPMENT_CREATED_ROUTING_KEY = "shipment.created";
    public static final String SHIPMENT_STATUS_CHANGED_ROUTING_KEY = "shipment.status-changed";
    public static final String SHIPMENT_CANCELLED_ROUTING_KEY = "shipment.cancelled";

    @Bean
    public TopicExchange shipmentExchange() {
        return new TopicExchange(SHIPMENT_EXCHANGE);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE);
    }

    @Bean
    public TopicExchange orderSagaDeadLetterExchange() {
        return new TopicExchange(ORDER_SAGA_DLX);
    }

    @Bean
    public Queue orderSagaQueue() {
        return new Queue(ORDER_SAGA_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", ORDER_SAGA_DLX,
                "x-dead-letter-routing-key", ORDER_SAGA_DLQ
        ));
    }

    @Bean
    public Queue orderSagaDeadLetterQueue() {
        return new Queue(ORDER_SAGA_DLQ, true);
    }

    @Bean
    public Binding orderCompletedBinding() {
        return BindingBuilder.bind(orderSagaQueue())
                .to(orderExchange())
                .with(ORDER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder.bind(orderSagaQueue())
                .to(orderExchange())
                .with(ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding orderSagaDeadLetterBinding() {
        return BindingBuilder.bind(orderSagaDeadLetterQueue())
                .to(orderSagaDeadLetterExchange())
                .with(ORDER_SAGA_DLQ);
    }
}
