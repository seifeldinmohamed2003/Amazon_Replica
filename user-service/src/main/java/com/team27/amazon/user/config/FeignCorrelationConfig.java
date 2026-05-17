package com.team27.amazon.user.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }

    @Bean
    public RequestInterceptor authorizationForwardInterceptor() {
        return template -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attrs == null) {
                return;
            }

            HttpServletRequest request = attrs.getRequest();
            String auth = request.getHeader("Authorization");

            if (auth != null && !auth.isBlank()) {
                template.header("Authorization", auth);
            }
        };
    }
}