package com.team27.amazon.billing.config;

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

    /** §2.9: order.completed, order.cancelled (not order.placed) */
    @Bean
    public Declarables orderEventConsumers() {
        String exchange = EventExchanges.ORDER_EVENTS;
        TopicExchange source = RabbitConsumerTopology.topicExchange(exchange);
        TopicExchange dlx = RabbitConsumerTopology.deadLetterExchange(exchange);
        return RabbitConsumerTopology.declarablesForConsumers(
                exchange,
                RabbitConsumerTopology.consumer(
                        EventQueueNames.BILLING_ORDER_COMPLETED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.ORDER_COMPLETED),
                RabbitConsumerTopology.consumer(
                        EventQueueNames.BILLING_ORDER_CANCELLED,
                        exchange,
                        source,
                        dlx,
                        EventRoutingKeys.ORDER_CANCELLED)
        );
    }
}
