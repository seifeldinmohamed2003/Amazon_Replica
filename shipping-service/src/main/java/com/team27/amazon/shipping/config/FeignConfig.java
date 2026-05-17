package com.team27.amazon.shipping.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Configuration
public class FeignConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 404) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Remote resource not found");
            }

            if (response.status() >= 500) {
                return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote service failure");
            }

            return new ErrorDecoder.Default().decode(methodKey, response);
        };
    }
}
