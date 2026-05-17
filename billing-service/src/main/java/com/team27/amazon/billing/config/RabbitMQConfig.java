package com.team27.amazon.billing.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // Billing declares its own exchange (producer side)
    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange("payment.events");
    }

    // Reference to order.events exchange (consumer side)
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

    // Bindings
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

    // Queue specifically for order.completed events
    @Bean
    public Queue orderCompletedQueue() {
        return QueueBuilder.durable("billing.order-completed")
                .withArgument("x-dead-letter-exchange", "payment.dlx")
                .withArgument("x-dead-letter-routing-key", "payment.saga-listener.dlq")
                .build();
    }

    // Queue specifically for order.cancelled events
    @Bean
    public Queue orderCancelledQueue() {
        return QueueBuilder.durable("billing.order-cancelled")
                .withArgument("x-dead-letter-exchange", "payment.dlx")
                .withArgument("x-dead-letter-routing-key", "payment.saga-listener.dlq")
                .build();
    }
}