package com.team27.amazon.billing.messaging;

import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionMethod;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.contracts.events.OrderCancelledEvent;
import com.team27.amazon.contracts.events.OrderCompletedEvent;
import com.team27.amazon.contracts.events.PaymentInitiatedEvent;
import com.team27.amazon.contracts.events.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final TransactionRepository transactionRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    @RabbitListener(queues = "payment.saga-listener")
    public void handleOrderEvent(Object event, @Headers Map<String, Object> headers) {
        String routingKey = (String) headers.get("amqp_receivedRoutingKey");
        log.info("Routing key: {}, event type: {}", routingKey, event.getClass().getName());

        if ("order.completed".equals(routingKey)) {
            if (event instanceof OrderCompletedEvent e) {
                handleOrderCompleted(e);
            } else if (event instanceof Map<?, ?> map) {
                OrderCompletedEvent e = new OrderCompletedEvent(
                        toLong(map.get("orderId")),
                        toLong(map.get("userId")),
                        toLong(map.get("shippingAddressId")),
                        toDouble(map.get("totalAmount"))
                );
                handleOrderCompleted(e);
            } else {
                log.warn("Unknown event type for order.completed: {}", event.getClass().getName());
            }
        } else if ("order.cancelled".equals(routingKey)) {
            if (event instanceof OrderCancelledEvent e) {
                handleOrderCancelled(e);
            } else if (event instanceof Map<?, ?> map) {
                OrderCancelledEvent e = new OrderCancelledEvent(
                        toLong(map.get("orderId")),
                        toLong(map.get("userId")),
                        null,
                        (String) map.get("reason")
                );
                handleOrderCancelled(e);
            } else {
                log.warn("Unknown event type for order.cancelled: {}", event.getClass().getName());
            }
        } else {
            log.warn("Unhandled routing key: {}", routingKey);
        }
    }

    private void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("Received order.completed for orderId={} userId={}", event.orderId(), event.userId());
        try {
            Transaction transaction = new Transaction();
            transaction.setOrderId(event.orderId());
            transaction.setUserId(event.userId());
            transaction.setAmount(event.totalAmount());
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setMethod(TransactionMethod.CREDIT_CARD);
            transaction.setCreatedAt(LocalDateTime.now());

            Transaction saved = transactionRepository.save(transaction);
            log.info("Created PENDING transaction id={} for orderId={}", saved.getId(), event.orderId());

            paymentEventPublisher.publishPaymentInitiated(
                    new PaymentInitiatedEvent(saved.getId(), event.orderId(), event.userId(), event.totalAmount())
            );
        } catch (Exception e) {
            log.error("Failed to process order.completed for orderId={}: {}", event.orderId(), e.getMessage());
            throw e;
        }
    }

    private void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("Received order.cancelled for orderId={} userId={}", event.orderId(), event.userId());
        try {
            Transaction transaction = transactionRepository.findPendingTransactionByOrderId(event.orderId())
                    .orElseThrow(() -> new RuntimeException("No transaction found for orderId=" + event.orderId()));

            transaction.setStatus(TransactionStatus.REFUNDED);
            transaction.setTransactionDetails(Map.of(
                    "refundReason", "Order cancelled: " + event.reason(),
                    "refundedAt", LocalDateTime.now().toString()
            ));

            Transaction saved = transactionRepository.save(transaction);
            log.info("Refunded transaction id={} for orderId={}", saved.getId(), event.orderId());

            paymentEventPublisher.publishPaymentRefunded(
                    new PaymentRefundedEvent(saved.getId(), event.orderId(), saved.getAmount())
            );
        } catch (Exception e) {
            log.error("Failed to process order.cancelled for orderId={}: {}", event.orderId(), e.getMessage());
            throw e;
        }
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Integer i) return i.longValue();
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Double d) return d;
        if (val instanceof Number n) return n.doubleValue();
        return Double.parseDouble(val.toString());
    }
}