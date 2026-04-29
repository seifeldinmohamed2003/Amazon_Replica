package com.team27.amazon.user.config;

import com.team27.amazon.common.events.AuthEvent;
import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.user.repository.AuthEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventLoggingConfiguration {

    @Bean
    public MongoEventLogger userMongoEventLogger(AuthEventRepository authEventRepository) {
        return new MongoEventLogger(
                EventType.AUTH,
                new EventFactory(),
                event -> authEventRepository.save((AuthEvent) event)
        );
    }
}