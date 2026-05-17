package com.team27.amazon.product.config;

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
    public TopicExchange productEventsExchange() {
        return RabbitConsumerTopology.topicExchange(EventExchanges.PRODUCT_EVENTS);
    }

    /** §2.9: order.placed (deduct stock), order.completed, order.cancelled (restore stock) */
    @Bean
    public Declarables orderEventConsumers() {
        String exchange = EventExchanges.ORDER_EVENTS;
        TopicExchange source = RabbitConsumerTopology.topicExchange(exchange);
        TopicExchange dlx = RabbitConsumerTopology.deadLetterExchange(exchange);
        return RabbitConsumerTopology.declarablesForConsumers(
                exchange,
                RabbitConsumerTopology.consumer(
                        EventQueueNames.PRODUCT_ORDER_PLACED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.ORDER_PLACED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.PRODUCT_ORDER_COMPLETED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.ORDER_COMPLETED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.PRODUCT_ORDER_CANCELLED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.ORDER_CANCELLED)
        );
    }
}
