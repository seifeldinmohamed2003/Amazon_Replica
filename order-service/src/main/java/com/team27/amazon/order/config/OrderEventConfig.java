package com.team27.amazon.order.config;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * S3-EVENTS RabbitMQ configuration for order-service.
 *
 * Declares:
 * - order.saga-feedback queue for consuming external saga events
 * - order.saga-feedback.dlq dead-letter queue
 * - Bindings from shipment.events and payment.events to order.saga-feedback
 */
@Configuration
public class OrderEventConfig {

    // Order Events Publishing Exchange is declared in RabbitTopologyConfig
    public static final String ORDER_EVENTS_EXCHANGE = EventExchanges.ORDER_EVENTS;

    // Order Saga Feedback Queues
    public static final String ORDER_SAGA_FEEDBACK_QUEUE = "order.saga-feedback";
    public static final String ORDER_SAGA_FEEDBACK_DLQ = "order.saga-feedback.dlq";
    public static final String ORDER_SAGA_FEEDBACK_DLX = "order.saga-feedback.dlx";

    // External Event Exchanges (consumed from)
    public static final String SHIPMENT_EVENTS_EXCHANGE = EventExchanges.SHIPMENT_EVENTS;
    public static final String PAYMENT_EVENTS_EXCHANGE = EventExchanges.PAYMENT_EVENTS;

    // ========== SAGA FEEDBACK QUEUE (Consuming) ==========
    @Bean
    public Queue orderSagaFeedbackQueue() {
        return new Queue(ORDER_SAGA_FEEDBACK_QUEUE, true,
                false, false,
                Map.of(
                        "x-dead-letter-exchange", ORDER_SAGA_FEEDBACK_DLX,
                        "x-dead-letter-routing-key", ORDER_SAGA_FEEDBACK_DLQ
                ));
    }

    @Bean
    public Queue orderSagaFeedbackDLQ() {
        return new Queue(ORDER_SAGA_FEEDBACK_DLQ, true, false, false);
    }

    @Bean
    public TopicExchange orderSagaFeedbackDLX() {
        return new TopicExchange(ORDER_SAGA_FEEDBACK_DLX, true, false);
    }

    @Bean
    public Binding bindDLQToDLX() {
        return BindingBuilder
                .bind(orderSagaFeedbackDLQ())
                .to(orderSagaFeedbackDLX())
                .with(ORDER_SAGA_FEEDBACK_DLQ);
    }

    // ========== SHIPMENT EVENTS BINDINGS ==========
    @Bean
    public TopicExchange shipmentEventsExchange() {
        return new TopicExchange(SHIPMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Binding bindShipmentCreatedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(shipmentEventsExchange())
                .with(EventRoutingKeys.SHIPMENT_CREATED);
    }

    @Bean
    public Binding bindShipmentStatusChangedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(shipmentEventsExchange())
                .with(EventRoutingKeys.SHIPMENT_STATUS_CHANGED);
    }

    // ========== PAYMENT EVENTS BINDINGS ==========
    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Binding bindPaymentInitiatedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(paymentEventsExchange())
                .with(EventRoutingKeys.PAYMENT_INITIATED);
    }

    @Bean
    public Binding bindPaymentCompletedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(paymentEventsExchange())
                .with(EventRoutingKeys.PAYMENT_COMPLETED);
    }

    @Bean
    public Binding bindPaymentFailedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(paymentEventsExchange())
                .with(EventRoutingKeys.PAYMENT_FAILED);
    }

    @Bean
    public Binding bindPaymentRefundedToSagaFeedback() {
        return BindingBuilder
                .bind(orderSagaFeedbackQueue())
                .to(paymentEventsExchange())
                .with(EventRoutingKeys.PAYMENT_REFUNDED);
    }

    // ========== MESSAGE CONVERTER ==========
    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}