package com.team27.amazon.billing.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(EventExchanges.PAYMENT_EVENTS, true, false);
    }

}
