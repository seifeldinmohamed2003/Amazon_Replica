package com.team27.amazon.order.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder
                .topicExchange(EventExchanges.ORDER_EVENTS)
                .durable(true)
                .build();
    }
}