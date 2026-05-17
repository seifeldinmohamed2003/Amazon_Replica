package com.team27.amazon.user.client;

import com.team27.amazon.contracts.feign.BillingServiceClient;
import com.team27.amazon.user.exception.ServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BillingServiceGateway {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceGateway.class);

    private final BillingServiceClient billingServiceClient;

    public BillingServiceGateway(BillingServiceClient billingServiceClient) {
        this.billingServiceClient = billingServiceClient;
    }

    public BigDecimal getUserTransactionTotal(Long userId, String startDate, String endDate) {
        try {
            return billingServiceClient.getUserTransactionTotal(userId, startDate, endDate);
        } catch (FeignException e) {
            log.warn("billing-service unavailable for user {} total: {}", userId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    public long getUserOrderCount(Long userId, String startDate, String endDate) {
        try {
            return billingServiceClient.getUserOrderCount(userId, startDate, endDate);
        } catch (FeignException e) {
            log.warn("billing-service unavailable for user {} order count: {}", userId, e.getMessage());
            return 0L;
        }
    }
}
