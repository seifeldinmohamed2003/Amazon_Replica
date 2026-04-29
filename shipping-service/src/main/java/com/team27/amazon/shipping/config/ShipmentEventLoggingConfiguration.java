package com.team27.amazon.shipping.config;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.common.events.ShipmentEvent;
import com.team27.amazon.shipping.repository.ShipmentEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShipmentEventLoggingConfiguration {

    @Bean
    public MongoEventLogger shipmentEventLogger(ShipmentEventRepository shipmentEventRepository) {
        return new MongoEventLogger(
                EventType.SHIPMENT,
                new EventFactory(),
                event -> shipmentEventRepository.save((ShipmentEvent) event)
        );
    }
}