package com.team27.amazon.billing.config;

import com.team27.amazon.billing.repository.TransactionAuditEventRepository;
import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.common.events.TransactionAuditEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BillingEventLoggingConfiguration {

    @Bean
    public MongoEventLogger billingEventLogger(TransactionAuditEventRepository transactionAuditEventRepository) {
        return new MongoEventLogger(
                EventType.TRANSACTION_AUDIT,
                new EventFactory(),
                event -> transactionAuditEventRepository.save((TransactionAuditEvent) event)
        );
    }
}