package com.team27.amazon.contracts.feign;

import com.team27.amazon.contracts.dto.ShipmentDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "shipping-service", url = "${feign.shipping-service.url}")
public interface ShippingServiceClient {

    @GetMapping("/api/shipments/order/{orderId}/active")
    ShipmentDTO getActiveShipmentForOrder(@PathVariable("orderId") Long orderId);

    @GetMapping("/api/shipments/order/{orderId}/ids")
    List<Long> getShipmentIdsForOrder(@PathVariable("orderId") Long orderId);
}
