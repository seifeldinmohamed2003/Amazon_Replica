package com.team27.amazon.shipping.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange shipmentEventsExchange() {
        return new TopicExchange(EventExchanges.SHIPMENT_EVENTS, true, false);
    }

}
