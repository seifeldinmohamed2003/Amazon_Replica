package com.team27.amazon.billing.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Billing declares its own exchange (producer side)
    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    // Billing consumes from order.events — declare the exchange reference
    @Bean
    public TopicExchange orderEventsExchange() {
        return new TopicExchange("order.events");
    }

    // Dead letter exchange
    @Bean
    public TopicExchange paymentDlx() {
        return new TopicExchange("payment.dlx");
    }

    // Consumer queue with DLQ wiring
    @Bean
    public Queue paymentSagaListenerQueue() {
        return QueueBuilder.durable("payment.saga-listener")
                .withArgument("x-dead-letter-exchange", "payment.dlx")
                .withArgument("x-dead-letter-routing-key", "payment.saga-listener.dlq")
                .build();
    }

    @Bean
    public Queue paymentSagaListenerDlq() {
        return QueueBuilder.durable("payment.saga-listener.dlq").build();
    }

    // Bind queue to order.events for order.completed and order.cancelled
    @Bean
    public Binding orderCompletedBinding() {
        return BindingBuilder
                .bind(paymentSagaListenerQueue())
                .to(orderEventsExchange())
                .with("order.completed");
    }

    @Bean
    public Binding orderCancelledBinding() {
        return BindingBuilder
                .bind(paymentSagaListenerQueue())
                .to(orderEventsExchange())
                .with("order.cancelled");
    }
}