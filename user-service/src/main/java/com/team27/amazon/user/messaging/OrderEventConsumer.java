package com.team27.amazon.user.messaging;

import com.team27.amazon.contracts.constants.EventQueueNames;
import com.team27.amazon.user.cache.CacheConstants;
import com.team27.amazon.user.cache.CacheInvalidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final CacheInvalidationService cacheInvalidationService;

    public OrderEventConsumer(CacheInvalidationService cacheInvalidationService) {
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @RabbitListener(queues = EventQueueNames.USER_ORDER_COMPLETED)
    public void onOrderCompleted(Map<String, Object> payload) {
        log.info("Received order.completed event: {}", payload);
        invalidateOrderRelatedCaches(payload);
    }

    @RabbitListener(queues = EventQueueNames.USER_ORDER_CANCELLED)
    public void onOrderCancelled(Map<String, Object> payload) {
        log.info("Received order.cancelled event: {}", payload);
        invalidateOrderRelatedCaches(payload);
    }

    private void invalidateOrderRelatedCaches(Map<String, Object> payload) {
        // Invalidate user-specific S1-F3 cache if userId is in the payload
        if (payload != null && payload.containsKey("userId")) {
            Long userId = ((Number) payload.get("userId")).longValue();
            cacheInvalidationService.deleteByPattern(
                    com.team27.amazon.user.cache.CacheConstants.SERVICE + "::" +
                    com.team27.amazon.user.cache.CacheConstants.S1_F3 + "::" + userId + "::*"
            );
        }
        // Invalidate S1-F6 and S1-F9 (global aggregates — not user-specific)
        cacheInvalidationService.deleteByPattern(
                com.team27.amazon.user.cache.CacheConstants.SERVICE + "::" +
                com.team27.amazon.user.cache.CacheConstants.S1_F6 + "::*"
        );
        cacheInvalidationService.deleteByPattern(
                com.team27.amazon.user.cache.CacheConstants.SERVICE + "::" +
                com.team27.amazon.user.cache.CacheConstants.S1_F9 + "::*"
        );
    }
}
