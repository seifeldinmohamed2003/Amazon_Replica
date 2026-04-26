package com.team27.amazon.billing.factory;

import com.team27.amazon.billing.mongo.model.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class EventFactory {

    /**
     * Creates the correct MongoEvent concrete type based on EventType.
     * params must contain at least "action". Service-specific IDs
     * (transactionId, orderId, etc.) are also read from params.
     */
    public static MongoEvent createEvent(EventType type, Map<String, Object> params) {
        if (params == null) params = new HashMap<>();

        return switch (type) {
            case TRANSACTION_AUDIT -> buildTransactionAuditEvent(params);
            case ORDER             -> buildOrderEvent(params);
            case SHIPMENT          -> buildShipmentEvent(params);
            default -> throw new IllegalArgumentException("Unsupported EventType: " + type);
        };
    }

    // ── builders ────────────────────────────────────────────────────────────

    private static TransactionAuditEvent buildTransactionAuditEvent(Map<String, Object> params) {
        TransactionAuditEvent e = new TransactionAuditEvent();
        e.setAction(str(params, "action"));
        e.setTimestamp(ts(params));
        e.setTransactionId(longVal(params, "transactionId"));
        e.setMethod(str(params, "method"));
        e.setAmount(doubleVal(params, "amount"));

        Map<String, Object> details = new HashMap<>(params);
        details.remove("action");
        details.remove("transactionId");
        details.remove("method");
        details.remove("amount");
        details.remove("timestamp");
        e.setDetails(details);
        return e;
    }

    private static OrderEvent buildOrderEvent(Map<String, Object> params) {
        OrderEvent e = new OrderEvent();
        e.setAction(str(params, "action"));
        e.setTimestamp(ts(params));
        e.setOrderId(longVal(params, "orderId"));
        Map<String, Object> details = new HashMap<>(params);
        details.remove("action");
        details.remove("orderId");
        details.remove("timestamp");
        e.setDetails(details);
        return e;
    }

    private static ShipmentEvent buildShipmentEvent(Map<String, Object> params) {
        ShipmentEvent e = new ShipmentEvent();
        e.setAction(str(params, "action"));
        e.setTimestamp(ts(params));
        e.setShipmentId(longVal(params, "shipmentId"));
        Map<String, Object> details = new HashMap<>(params);
        details.remove("action");
        details.remove("shipmentId");
        details.remove("timestamp");
        e.setDetails(details);
        return e;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v != null ? v.toString() : null;
    }

    private static Long longVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.longValue();
        return null;
    }

    private static Double doubleVal(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    private static LocalDateTime ts(Map<String, Object> p) {
        Object v = p.get("timestamp");
        if (v instanceof LocalDateTime ldt) return ldt;
        return LocalDateTime.now();
    }
}
