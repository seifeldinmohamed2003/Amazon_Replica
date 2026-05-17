package com.team27.amazon.product.messaging.publishers;

import com.team27.amazon.contracts.constants.EventExchanges;
import com.team27.amazon.contracts.constants.EventRoutingKeys;
import com.team27.amazon.contracts.events.ProductDiscontinuedEvent;
import com.team27.amazon.contracts.events.ProductRatedEvent;
import com.team27.amazon.contracts.events.ProductReviewAddedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ProductEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishProductDiscontinued(Long productId, String oldStatus, String newStatus) {
        String routingKey = EventRoutingKeys.PRODUCT_DISCONTINUED;

        try {
            MDC.put("productId", String.valueOf(productId));
            MDC.put("routingKey", routingKey);

            ProductDiscontinuedEvent event =
                    new ProductDiscontinuedEvent(productId, oldStatus, newStatus);

            rabbitTemplate.convertAndSend(EventExchanges.PRODUCT_EVENTS, routingKey, event);

            log.info("Published product discontinued event for productId={}", productId);
        } finally {
            MDC.remove("productId");
            MDC.remove("routingKey");
        }
    }

    public void publishProductReviewAdded(Long productId, Long reviewId, Long userId, Integer rating) {
        String routingKey = EventRoutingKeys.PRODUCT_REVIEW_ADDED;

        try {
            MDC.put("productId", String.valueOf(productId));
            MDC.put("routingKey", routingKey);

            ProductReviewAddedEvent event =
                    new ProductReviewAddedEvent(productId, reviewId, userId, rating);

            rabbitTemplate.convertAndSend(EventExchanges.PRODUCT_EVENTS, routingKey, event);

            log.info("Published product review added event for productId={}, reviewId={}", productId, reviewId);
        } finally {
            MDC.remove("productId");
            MDC.remove("routingKey");
        }
    }

    public void publishProductRated(Long productId, Double newAverage, Integer totalRatings) {
        String routingKey = EventRoutingKeys.PRODUCT_RATED;

        try {
            MDC.put("productId", String.valueOf(productId));
            MDC.put("routingKey", routingKey);

            ProductRatedEvent event =
                    new ProductRatedEvent(productId, newAverage, totalRatings);

            rabbitTemplate.convertAndSend(EventExchanges.PRODUCT_EVENTS, routingKey, event);

            log.info("Published product rated event for productId={}, average={}, totalRatings={}",
                    productId, newAverage, totalRatings);
        } finally {
            MDC.remove("productId");
            MDC.remove("routingKey");
        }
    }
}