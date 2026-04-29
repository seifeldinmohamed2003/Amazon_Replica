package com.team27.amazon.product.config;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.common.events.ProductEvent;
import com.team27.amazon.product.repository.ProductEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductEventLoggingConfiguration {

    @Bean(name = "productEventLogger")
    public MongoEventLogger productEventLogger(ProductEventRepository productEventRepository) {
        return new MongoEventLogger(
                EventType.PRODUCT,
                new EventFactory(),
                event -> productEventRepository.save((ProductEvent) event)
        );
    }

    @Bean(name = "productReviewEventLogger")
    public MongoEventLogger productReviewEventLogger(ProductEventRepository productEventRepository) {
        return new MongoEventLogger(
                EventType.PRODUCT,
                new EventFactory(),
                event -> productEventRepository.save((ProductEvent) event)
        );
    }
}