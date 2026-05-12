package com.team27.amazon.order.config.rabbit;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String SHIPPING_EXCHANGE = "shipping.events";

    public static final String ORDER_COMPLETED_ROUTING_KEY = "order.completed";

    public static final String ORDER_CANCELLED_ROUTING_KEY = "order.cancelled";

    @Bean
    public TopicExchange shippingExchange() {
        return new TopicExchange(SHIPPING_EXCHANGE);
    }
}
