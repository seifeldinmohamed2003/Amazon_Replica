package com.team27.amazon.common.events;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventFactory {

    private static final Map<EventType, String> EVENT_CLASS_NAMES = Map.of(
            EventType.AUTH, "com.team27.amazon.user.model.mongo.AuthEvent",
            EventType.PRODUCT, "com.team27.amazon.product.model.mongo.ProductEvent",
            EventType.ORDER, "com.team27.amazon.order.model.mongo.OrderEvent",
            EventType.SHIPMENT, "com.team27.amazon.shipping.model.mongo.ShipmentEvent",
            EventType.TRANSACTION_AUDIT, "com.team27.amazon.billing.model.mongo.TransactionAuditEvent"
    );

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null");
        }

        Map<String, Object> safeParams = params == null ? new HashMap<>() : new LinkedHashMap<>(params);
        MongoEvent event = instantiateEvent(type);

        invokeIfPresent(event, "setAction", asString(safeParams.get("action")));
        invokeIfPresent(event, "setTimestamp", asLocalDateTime(safeParams.get("timestamp"), LocalDateTime.now()));
        invokeIfPresent(event, "setDetails", asDetailsMap(safeParams.get("details")));
        invokeIfPresent(event, "setUserId", asLong(safeParams.get("userId")));
        invokeIfPresent(event, "setProductId", asLong(safeParams.get("productId")));
        invokeIfPresent(event, "setOrderId", asLong(safeParams.get("orderId")));
        invokeIfPresent(event, "setShipmentId", asLong(safeParams.get("shipmentId")));
        invokeIfPresent(event, "setTransactionId", asLong(safeParams.get("transactionId")));
        invokeIfPresent(event, "setEmail", asString(safeParams.get("email")));
        invokeIfPresent(event, "setMethod", asString(safeParams.get("method")));
        invokeIfPresent(event, "setAmount", asDouble(safeParams.get("amount")));

        return event;
    }

    private MongoEvent instantiateEvent(EventType type) {
        String className = EVENT_CLASS_NAMES.get(type);
        if (className == null) {
            throw new IllegalArgumentException("Unsupported event type: " + type);
        }

        try {
            Class<?> eventClass = Class.forName(className);
            Constructor<?> constructor = eventClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (MongoEvent) constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to create event for type: " + type, ex);
        }
    }

    private void invokeIfPresent(Object target, String methodName, Object value) {
        if (value == null) {
            return;
        }

        Method method = findSingleArgumentMethod(target.getClass(), methodName);
        if (method == null) {
            return;
        }

        try {
            method.setAccessible(true);
            method.invoke(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke " + methodName + " on " + target.getClass().getName(), ex);
        }
    }

    private Method findSingleArgumentMethod(Class<?> targetClass, String methodName) {
        for (Method method : targetClass.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue.trim());
        }
        throw new IllegalArgumentException("Cannot convert value to Long: " + value);
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Double.parseDouble(stringValue.trim());
        }
        throw new IllegalArgumentException("Cannot convert value to Double: " + value);
    }

    private LocalDateTime asLocalDateTime(Object value, LocalDateTime defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return LocalDateTime.parse(stringValue.trim());
        }
        throw new IllegalArgumentException("Cannot convert value to LocalDateTime: " + value);
    }

    private Map<String, Object> asDetailsMap(Object value) {
        if (value == null) {
            return new HashMap<>();
        }
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> details = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                details.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return details;
        }
        throw new IllegalArgumentException("Cannot convert value to Map<String, Object>: " + value);
    }
}
