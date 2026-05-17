package com.team27.amazon.billing.messaging;

import com.team27.amazon.contracts.events.PaymentInitiatedEvent;
import com.team27.amazon.contracts.events.PaymentCompletedEvent;
import com.team27.amazon.contracts.events.PaymentFailedEvent;
import com.team27.amazon.contracts.events.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE = "payment.events";

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.initiated", event);
        log.info("Published payment.initiated for order {}", event.orderId());
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.completed", event);
        log.info("Published payment.completed for order {}", event.orderId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.failed", event);
        log.info("Published payment.failed for order {}", event.orderId());
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, "payment.refunded", event);
        log.info("Published payment.refunded for order {}", event.orderId());
    }
}