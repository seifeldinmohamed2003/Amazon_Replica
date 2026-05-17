package com.team27.amazon.shipping.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShippingRabbitMQConfig {

    public static final String SHIPMENT_EVENTS_EXCHANGE = "shipment.events";
    public static final String ORDER_EVENTS_EXCHANGE = "order.events";

    public static final String SHIPMENT_SAGA_QUEUE = "shipment.saga-listener";
    public static final String SHIPMENT_SAGA_DLQ = "shipment.saga-listener.dlq";
    public static final String SHIPMENT_SAGA_DLX = "shipment.saga-listener.dlx";

    public static final String ORDER_PLACED_ROUTING_KEY = "order.placed";
    public static final String ORDER_COMPLETED_ROUTING_KEY = "order.completed";
    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

    public static final String SHIPMENT_CREATED_ROUTING_KEY = "shipment.created";
    public static final String SHIPMENT_STATUS_CHANGED_ROUTING_KEY = "shipment.status-changed";
    public static final String SHIPMENT_CANCELLED_ROUTING_KEY = "shipment.cancelled";

    @Bean
    public TopicExchange shipmentEventsExchange() {
        return ExchangeBuilder
                .topicExchange(SHIPMENT_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder
                .topicExchange(ORDER_EVENTS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange shipmentSagaDeadLetterExchange() {
        return ExchangeBuilder
                .directExchange(SHIPMENT_SAGA_DLX)
                .durable(true)
                .build();
    }

    @Bean
    public Queue shipmentSagaQueue() {
        return QueueBuilder
                .durable(SHIPMENT_SAGA_QUEUE)
                .withArgument("x-dead-letter-exchange", SHIPMENT_SAGA_DLX)
                .withArgument("x-dead-letter-routing-key", SHIPMENT_SAGA_DLQ)
                .build();
    }

    @Bean
    public Queue shipmentSagaDeadLetterQueue() {
        return QueueBuilder
                .durable(SHIPMENT_SAGA_DLQ)
                .build();
    }

    @Bean
    public Binding shipmentSagaDeadLetterBinding() {
        return BindingBuilder
                .bind(shipmentSagaDeadLetterQueue())
                .to(shipmentSagaDeadLetterExchange())
                .with(SHIPMENT_SAGA_DLQ);
    }

    @Bean
    public Binding orderPlacedToShipmentSagaBinding() {
        return BindingBuilder
                .bind(shipmentSagaQueue())
                .to(orderEventsExchange())
                .with(ORDER_PLACED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCompletedToShipmentSagaBinding() {
        return BindingBuilder
                .bind(shipmentSagaQueue())
                .to(orderEventsExchange())
                .with(ORDER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding orderCancelledToShipmentSagaBinding() {
        return BindingBuilder
                .bind(shipmentSagaQueue())
                .to(orderEventsExchange())
                .with(ORDER_CANCELLED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}