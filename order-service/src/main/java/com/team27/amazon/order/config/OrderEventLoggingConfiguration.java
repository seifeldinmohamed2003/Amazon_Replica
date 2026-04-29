package com.team27.amazon.order.config;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.common.events.OrderEvent;
import com.team27.amazon.order.repository.OrderEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderEventLoggingConfiguration {

    @Bean
    public MongoEventLogger orderEventLogger(OrderEventRepository orderEventRepository) {
        return new MongoEventLogger(
                EventType.ORDER,
                new EventFactory(),
                event -> orderEventRepository.save((OrderEvent) event)
        );
    }
}