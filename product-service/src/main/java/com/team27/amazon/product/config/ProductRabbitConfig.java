package com.team27.amazon.product.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@EnableRabbit
@Configuration
public class ProductRabbitConfig {

    public static final String PRODUCT_ORDER_QUEUE = "product.order.saga-listener";
    public static final String PRODUCT_ORDER_DLQ = "product.order.saga-listener.dlq";
    public static final String PRODUCT_ORDER_DLX = "product.order.saga-listener.dlx";

    @Bean
    public TopicExchange productEventsExchange() {
        return ExchangeBuilder
                .topicExchange(EventExchanges.PRODUCT_EVENTS)
                .durable(true)
                .build();
    }

    @Bean
    public TopicExchange orderEventsExchange() {
        return ExchangeBuilder
                .topicExchange(EventExchanges.ORDER_EVENTS)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange productOrderDeadLetterExchange() {
        return ExchangeBuilder
                .directExchange(PRODUCT_ORDER_DLX)
                .durable(true)
                .build();
    }

    @Bean
    public Queue productOrderSagaQueue() {
        return QueueBuilder
                .durable(PRODUCT_ORDER_QUEUE)
                .deadLetterExchange(PRODUCT_ORDER_DLX)
                .deadLetterRoutingKey(PRODUCT_ORDER_DLQ)
                .build();
    }

    @Bean
    public Queue productOrderSagaDlq() {
        return QueueBuilder
                .durable(PRODUCT_ORDER_DLQ)
                .build();
    }

    @Bean
    public Binding bindOrderPlacedToProductQueue() {
        return BindingBuilder
                .bind(productOrderSagaQueue())
                .to(orderEventsExchange())
                .with(EventRoutingKeys.ORDER_PLACED);
    }

    @Bean
    public Binding bindOrderCompletedToProductQueue() {
        return BindingBuilder
                .bind(productOrderSagaQueue())
                .to(orderEventsExchange())
                .with(EventRoutingKeys.ORDER_COMPLETED);
    }

    @Bean
    public Binding bindOrderCancelledToProductQueue() {
        return BindingBuilder
                .bind(productOrderSagaQueue())
                .to(orderEventsExchange())
                .with(EventRoutingKeys.ORDER_CANCELLED);
    }

    @Bean
    public Binding bindProductOrderSagaDlq() {
        return BindingBuilder
                .bind(productOrderSagaDlq())
                .to(productOrderDeadLetterExchange())
                .with(PRODUCT_ORDER_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}