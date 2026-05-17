package com.team27.amazon.order.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventQueueNames;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.rabbit.RabbitConsumerTopology;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public ApplicationRunner declareTopology(RabbitAdmin rabbitAdmin) {
        return args -> rabbitAdmin.initialize();
    }

    @Bean
    public TopicExchange orderEventsExchange() {
        return RabbitConsumerTopology.topicExchange(EventExchanges.ORDER_EVENTS);
    }

    /** §2.9: all payment.events consumers */
    @Bean
    public Declarables paymentEventConsumers() {
        String exchange = EventExchanges.PAYMENT_EVENTS;
        TopicExchange source = RabbitConsumerTopology.topicExchange(exchange);
        TopicExchange dlx = RabbitConsumerTopology.deadLetterExchange(exchange);
        return RabbitConsumerTopology.declarablesForConsumers(
                exchange,
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_PAYMENT_INITIATED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.PAYMENT_INITIATED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_PAYMENT_COMPLETED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.PAYMENT_COMPLETED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_PAYMENT_FAILED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.PAYMENT_FAILED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_PAYMENT_REFUNDED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.PAYMENT_REFUNDED)
        );
    }

    /** §2.9: shipment.created, shipment.status-changed */
    @Bean
    public Declarables shipmentEventConsumers() {
        String exchange = EventExchanges.SHIPMENT_EVENTS;
        TopicExchange source = RabbitConsumerTopology.topicExchange(exchange);
        TopicExchange dlx = RabbitConsumerTopology.deadLetterExchange(exchange);
        return RabbitConsumerTopology.declarablesForConsumers(
                exchange,
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_SHIPMENT_CREATED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.SHIPMENT_CREATED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.ORDER_SHIPMENT_STATUS_CHANGED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.SHIPMENT_STATUS_CHANGED)
        );
    }
}
