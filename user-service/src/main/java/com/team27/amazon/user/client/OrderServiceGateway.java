package com.team27.amazon.user.client;

import com.team27.amazon.contracts.dto.OrderSummaryDTO;
import com.team27.amazon.contracts.feign.OrderServiceClient;
import com.team27.amazon.user.exception.ServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceGateway.class);

    private final OrderServiceClient orderServiceClient;

    public OrderServiceGateway(OrderServiceClient orderServiceClient) {
        this.orderServiceClient = orderServiceClient;
    }

    public OrderSummaryDTO getUserOrderSummary(Long userId) {
        try {
            return orderServiceClient.getUserOrderSummary(userId);
        } catch (FeignException.NotFound e) {
            return OrderSummaryDTO.empty();
        } catch (FeignException e) {
            log.warn("order-service unavailable for user {}: {}", userId, e.getMessage());
            throw new ServiceUnavailableException("Order service temporarily unavailable");
        }
    }

    public int getActiveOrderCount(Long userId) {
        try {
            return orderServiceClient.getActiveOrderCount(userId);
        } catch (FeignException e) {
            log.warn("order-service unavailable fetching active count for user {}: {}", userId, e.getMessage());
            throw new ServiceUnavailableException("Order service temporarily unavailable");
        }
    }

    public long getTotalOrderCount(Long userId) {
        try {
            return orderServiceClient.getTotalOrderCount(userId);
        } catch (FeignException e) {
            log.warn("order-service unavailable fetching total count for user {}: {}", userId, e.getMessage());
            return 0L;
        }
    }
}
