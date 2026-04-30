package com.team27.amazon.billing.adapter;

import com.team27.amazon.billing.dto.AuditLogDTO;
import com.team27.amazon.billing.model.AuditLogDocument;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {
    public AuditLogDTO toDTO(AuditLogDocument doc) {
        if (doc == null) {
            return null;
        }

        return AuditLogDTO.builder()
                .transactionId(doc.getTransactionId())
                .eventType(doc.getEventType())
                .details(doc.getDetails())
                .timestamp(doc.getTimestamp())
                .build();
    }
}