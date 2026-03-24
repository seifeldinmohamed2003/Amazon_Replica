package com.team27.amazon.product.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class HealthController {
    @GetMapping("/api/products/health")
    public String health() {
        return "OK";
    }
}
