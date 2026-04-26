package com.team27.amazon.billing.observer;

import com.team27.amazon.billing.factory.EventFactory;
import com.team27.amazon.billing.factory.EventType;
import com.team27.amazon.billing.mongo.model.MongoEvent;
import com.team27.amazon.billing.mongo.model.TransactionAuditEvent;
import com.team27.amazon.billing.mongo.repository.TransactionAuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * GoF Observer — persists TransactionAuditEvents to MongoDB.
 * Billing service always binds EventType.TRANSACTION_AUDIT.
 * Mongo failures are caught and logged at WARN; never rethrown.
 */
public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final TransactionAuditEventRepository repository;
    private final EventType boundEventType;

    public MongoEventLogger(TransactionAuditEventRepository repository,
                            EventType boundEventType) {
        this.repository      = repository;
        this.boundEventType  = boundEventType;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("action", eventType);

            if (payload instanceof Map<?, ?> map) {
                map.forEach((k, v) -> params.put(String.valueOf(k), v));
            }

            MongoEvent event = EventFactory.createEvent(boundEventType, params);
            if (event instanceof TransactionAuditEvent tae) {
                repository.save(tae);
            }
        } catch (Exception ex) {
            log.warn("MongoEventLogger failed to persist event [{}]: {}", eventType, ex.getMessage());
        }
    }
}
