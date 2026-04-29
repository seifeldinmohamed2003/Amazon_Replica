package com.team27.amazon.common.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class MongoEventLogger implements EntityObserver {

    private static final Logger log = LoggerFactory.getLogger(MongoEventLogger.class);

    private final EventType boundEventType;
    private final EventFactory eventFactory;
    private final Consumer<MongoEvent> eventSaver;

    public MongoEventLogger(EventType boundEventType, EventFactory eventFactory, Consumer<MongoEvent> eventSaver) {
        this.boundEventType = Objects.requireNonNull(boundEventType, "boundEventType must not be null");
        this.eventFactory = Objects.requireNonNull(eventFactory, "eventFactory must not be null");
        this.eventSaver = Objects.requireNonNull(eventSaver, "eventSaver must not be null");
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = toParams(payload);
            params.put("action", eventType);

            MongoEvent event = eventFactory.createEvent(boundEventType, params);
            eventSaver.accept(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist {} event for action {}", boundEventType, eventType, ex);
        }
    }

    private Map<String, Object> toParams(Object payload) {
        Map<String, Object> params = new LinkedHashMap<>();

        if (payload == null) {
            return params;
        }

        if (payload instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                params.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return params;
        }

        params.put("payload", payload);
        return params;
    }
}