package com.team27.amazon.billing.logging;

import com.team27.amazon.billing.model.AuditLogDocument;
import com.team27.amazon.billing.repository.TransactionAuditRepository;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MongoEventLogger {

    private final TransactionAuditRepository auditRepository;

    public MongoEventLogger(TransactionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void logEvent(String eventType, String transactionId) {
        AuditLogDocument log = new AuditLogDocument(
            transactionId,
            eventType,
            "Event processed by billing-service",
            LocalDateTime.now()
        );
        auditRepository.save(log);
    }
}