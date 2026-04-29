package com.team27.amazon.common.events;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EventFactory {

    public MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (type == null) {
            throw new IllegalArgumentException("Event type must not be null");
        }

        Map<String, Object> safeParams = params == null ? new HashMap<>() : new LinkedHashMap<>(params);
        return switch (type) {
            case AUTH -> buildAuthEvent(safeParams);
            case PRODUCT -> buildProductEvent(safeParams);
            case ORDER -> buildOrderEvent(safeParams);
            case SHIPMENT -> buildShipmentEvent(safeParams);
            case TRANSACTION_AUDIT -> buildTransactionAuditEvent(safeParams);
        };
    }

    private AuthEvent buildAuthEvent(Map<String, Object> params) {
        AuthEvent event = new AuthEvent();
        event.setUserId(asLong(params.get("userId")));
        event.setEmail(asString(params.get("email")));
        applyCommonFields(event, params);
        return event;
    }

    private ProductEvent buildProductEvent(Map<String, Object> params) {
        ProductEvent event = new ProductEvent();
        event.setProductId(asLong(params.get("productId")));
        applyCommonFields(event, params);
        return event;
    }

    private OrderEvent buildOrderEvent(Map<String, Object> params) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(asLong(params.get("orderId")));
        applyCommonFields(event, params);
        return event;
    }

    private ShipmentEvent buildShipmentEvent(Map<String, Object> params) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipmentId(asLong(params.get("shipmentId")));
        applyCommonFields(event, params);
        return event;
    }

    private TransactionAuditEvent buildTransactionAuditEvent(Map<String, Object> params) {
        TransactionAuditEvent event = new TransactionAuditEvent();
        event.setTransactionId(asLong(params.get("transactionId")));
        event.setMethod(asString(params.get("method")));
        event.setAmount(asDouble(params.get("amount")));
        applyCommonFields(event, params);
        return event;
    }

    private void applyCommonFields(MongoEvent event, Map<String, Object> params) {
        if (event instanceof AuthEvent authEvent) {
            authEvent.setAction(asString(params.get("action")));
            authEvent.setTimestamp(asLocalDateTime(params.get("timestamp"), LocalDateTime.now()));
            authEvent.setDetails(asDetailsMap(params.get("details")));
            return;
        }
        if (event instanceof ProductEvent productEvent) {
            productEvent.setAction(asString(params.get("action")));
            productEvent.setTimestamp(asLocalDateTime(params.get("timestamp"), LocalDateTime.now()));
            productEvent.setDetails(asDetailsMap(params.get("details")));
            return;
        }
        if (event instanceof OrderEvent orderEvent) {
            orderEvent.setAction(asString(params.get("action")));
            orderEvent.setTimestamp(asLocalDateTime(params.get("timestamp"), LocalDateTime.now()));
            orderEvent.setDetails(asDetailsMap(params.get("details")));
            return;
        }
        if (event instanceof ShipmentEvent shipmentEvent) {
            shipmentEvent.setAction(asString(params.get("action")));
            shipmentEvent.setTimestamp(asLocalDateTime(params.get("timestamp"), LocalDateTime.now()));
            shipmentEvent.setDetails(asDetailsMap(params.get("details")));
            return;
        }
        if (event instanceof TransactionAuditEvent transactionAuditEvent) {
            transactionAuditEvent.setAction(asString(params.get("action")));
            transactionAuditEvent.setTimestamp(asLocalDateTime(params.get("timestamp"), LocalDateTime.now()));
            transactionAuditEvent.setDetails(asDetailsMap(params.get("details")));
        }
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
