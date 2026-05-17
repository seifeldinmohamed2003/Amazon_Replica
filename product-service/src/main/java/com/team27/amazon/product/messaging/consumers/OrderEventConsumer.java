package com.team27.amazon.product.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.events.OrderCancelledEvent;
import com.team27.amazon.contracts.events.OrderCompletedEvent;
import com.team27.amazon.contracts.events.OrderItemPayload;
import com.team27.amazon.contracts.events.OrderPlacedEvent;
import com.team27.amazon.product.config.ProductRabbitConfig;
import com.team27.amazon.product.service.ProductSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProductSagaService productSagaService;

    public OrderEventConsumer(ObjectMapper objectMapper,
                              ProductSagaService productSagaService) {
        this.objectMapper = objectMapper;
        this.productSagaService = productSagaService;
    }

    @RabbitListener(queues = ProductRabbitConfig.PRODUCT_ORDER_QUEUE)
    public void consume(Message message) throws Exception {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        if (EventRoutingKeys.ORDER_PLACED.equals(routingKey)) {
            OrderPlacedEvent event = objectMapper.readValue(message.getBody(), OrderPlacedEvent.class);
            handleOrderPlaced(event);
            return;
        }

        if (EventRoutingKeys.ORDER_COMPLETED.equals(routingKey)) {
            OrderCompletedEvent event = objectMapper.readValue(message.getBody(), OrderCompletedEvent.class);
            handleOrderCompleted(event);
            return;
        }

        if (EventRoutingKeys.ORDER_CANCELLED.equals(routingKey)) {
            OrderCancelledEvent event = objectMapper.readValue(message.getBody(), OrderCancelledEvent.class);
            handleOrderCancelled(event);
            return;
        }

        log.warn("Ignoring unsupported routingKey={} in product order saga queue", routingKey);
    }

    private void handleOrderPlaced(OrderPlacedEvent event) {
        try {
            MDC.put("orderId", String.valueOf(event.orderId()));
            MDC.put("routingKey", EventRoutingKeys.ORDER_PLACED);

            log.info("Consuming order.placed for orderId={}", event.orderId());

            if (event.items() != null) {
                for (OrderItemPayload item : event.items()) {
                    productSagaService.deductStock(event.orderId(), item.productId(), item.quantity());
                }
            }

            productSagaService.invalidateSalesAffectedCaches();

            log.info("Processed order.placed for orderId={}", event.orderId());
        } finally {
            MDC.remove("orderId");
            MDC.remove("routingKey");
        }
    }

    private void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            MDC.put("orderId", String.valueOf(event.orderId()));
            MDC.put("routingKey", EventRoutingKeys.ORDER_COMPLETED);

            log.info("Consuming order.completed for orderId={}", event.orderId());

            productSagaService.invalidateSalesAffectedCaches();

            log.info("Processed order.completed for orderId={}", event.orderId());
        } finally {
            MDC.remove("orderId");
            MDC.remove("routingKey");
        }
    }

    private void handleOrderCancelled(OrderCancelledEvent event) {
        try {
            MDC.put("orderId", String.valueOf(event.orderId()));
            MDC.put("routingKey", EventRoutingKeys.ORDER_CANCELLED);

            log.info("Consuming order.cancelled for orderId={}", event.orderId());

            if (event.restoredItems() != null) {
                for (OrderItemPayload item : event.restoredItems()) {
                    productSagaService.restoreStock(event.orderId(), item.productId(), item.quantity());
                }
            }

            productSagaService.invalidateSalesAffectedCaches();

            log.info("Processed order.cancelled for orderId={}", event.orderId());
        } finally {
            MDC.remove("orderId");
            MDC.remove("routingKey");
        }
    }
}