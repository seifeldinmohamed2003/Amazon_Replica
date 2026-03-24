package com.team27.amazon.order.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class HealthController {
    @GetMapping("/api/orders/health")
    public String health() {
        return "OK";
    }
}
