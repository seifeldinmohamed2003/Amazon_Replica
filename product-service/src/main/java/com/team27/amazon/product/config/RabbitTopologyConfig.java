package com.team27.amazon.product.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange productEventsExchange() {
        return new TopicExchange(EventExchanges.PRODUCT_EVENTS, true, false);
    }

}
