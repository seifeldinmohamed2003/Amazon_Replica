package com.team27.amazon.user.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(EventExchanges.USER_EVENTS, true, false);
    }

}
