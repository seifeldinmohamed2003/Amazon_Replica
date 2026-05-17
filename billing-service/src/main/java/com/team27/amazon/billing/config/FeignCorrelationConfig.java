package com.team27.amazon.billing.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import feign.codec.ErrorDecoder;
import feign.Response;
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new ErrorDecoder() {
            private final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            return switch (response.status()) {
                case 404 -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Downstream resource not found [" + methodKey + "]");
                case 503 -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "Downstream service unavailable [" + methodKey + "]");
                default -> response.status() >= 500
                        ? new ResponseStatusException(
                                org.springframework.http.HttpStatus.BAD_GATEWAY,
                                "Downstream error " + response.status() + " [" + methodKey + "]")
                        : defaultDecoder.decode(methodKey, response);
            };
        }
    };
}
}