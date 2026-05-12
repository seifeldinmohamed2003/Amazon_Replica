package com.team27.amazon.shipping.client;

import com.team27.amazon.shipping.dto.external.OrderResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", url = "${feign.order-service.url}")
public interface OrderServiceClient {

    @GetMapping("/api/orders/{orderId}")
    OrderResponse getOrderById(@PathVariable Long orderId);
}
