package com.team27.amazon.billing.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

public class HealthController {
    @GetMapping("/api/transactions/health")
    public String health() {
        return "OK";
    }
}
