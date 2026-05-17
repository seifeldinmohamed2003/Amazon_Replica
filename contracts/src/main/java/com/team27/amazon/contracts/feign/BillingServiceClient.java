package com.team27.amazon.contracts.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(name = "billing-service", url = "${FEIGN_BILLING_SERVICE_URL:http://billing-service:8080}")
public interface BillingServiceClient {

    @GetMapping("/api/transactions/user/{userId}/total")
    BigDecimal getUserTransactionTotal(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );

    @GetMapping("/api/transactions/user/{userId}/order-count")
    long getUserOrderCount(
            @PathVariable("userId") Long userId,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate
    );
}
