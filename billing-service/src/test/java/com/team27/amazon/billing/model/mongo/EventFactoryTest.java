package com.team27.amazon.billing.model.mongo;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEvent;
import com.team27.amazon.common.events.TransactionAuditEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    private final EventFactory factory = new EventFactory();

    @Test
    void createEventCreatesTransactionAuditEvent() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 29, 13, 30);
        MongoEvent event = factory.createEvent(EventType.TRANSACTION_AUDIT, Map.of(
                "transactionId", 500L,
                "action", "REFUNDED",
                "timestamp", timestamp,
                "method", "CREDIT_CARD",
                "amount", 123.45,
                "details", Map.of("strategy", "PartialItemRefundStrategy")
        ));

        assertInstanceOf(TransactionAuditEvent.class, event);

        TransactionAuditEvent auditEvent = (TransactionAuditEvent) event;
        assertEquals(500L, auditEvent.getTransactionId());
        assertEquals("REFUNDED", auditEvent.getAction());
        assertEquals(timestamp, auditEvent.getTimestamp());
        assertEquals("CREDIT_CARD", auditEvent.getMethod());
        assertEquals(123.45, auditEvent.getAmount(), 0.000001);
        assertEquals("PartialItemRefundStrategy", auditEvent.getDetails().get("strategy"));
    }
}
