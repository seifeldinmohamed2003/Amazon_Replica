package com.team27.amazon.common.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    @Test
    void mongoEventInterfaceExposesExpectedContract() {
        assertTrue(MongoEvent.class.isInterface());
        assertEquals(4, MongoEvent.class.getDeclaredMethods().length);
        assertNotNull(findMethod(MongoEvent.class, "getId"));
        assertNotNull(findMethod(MongoEvent.class, "getTimestamp"));
        assertNotNull(findMethod(MongoEvent.class, "getAction"));
        assertNotNull(findMethod(MongoEvent.class, "getDetails"));
    }

    @Test
    void allConcreteEventsImplementCommonMongoEvent() {
        assertTrue(MongoEvent.class.isAssignableFrom(AuthEvent.class));
        assertTrue(MongoEvent.class.isAssignableFrom(ProductEvent.class));
        assertTrue(MongoEvent.class.isAssignableFrom(OrderEvent.class));
        assertTrue(MongoEvent.class.isAssignableFrom(ShipmentEvent.class));
        assertTrue(MongoEvent.class.isAssignableFrom(TransactionAuditEvent.class));
    }

    @Test
    void eventFactoryExposesExpectedMethod() {
        Method method = findMethod(EventFactory.class, "createEvent", EventType.class, Map.class);
        assertNotNull(method);
        assertEquals(MongoEvent.class, method.getReturnType());
    }

    @Test
    void eventTypeContainsExpectedValues() {
        assertArrayEquals(
                new EventType[]{EventType.AUTH, EventType.PRODUCT, EventType.ORDER, EventType.SHIPMENT, EventType.TRANSACTION_AUDIT},
                EventType.values()
        );
    }

    @Test
    void createEventReturnsExpectedConcreteTypesAndMapsFields() {
        EventFactory factory = new EventFactory();

        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 30, 10, 15);

        AuthEvent authEvent = (AuthEvent) factory.createEvent(EventType.AUTH, Map.of(
                "userId", 15,
                "email", "auth@example.com",
                "action", "REGISTERED",
                "timestamp", timestamp,
                "details", Map.of("source", "ui")
        ));
        assertEquals(15L, authEvent.getUserId());
        assertEquals("auth@example.com", authEvent.getEmail());
        assertEquals("REGISTERED", authEvent.getAction());
        assertEquals(timestamp, authEvent.getTimestamp());
        assertEquals("ui", authEvent.getDetails().get("source"));

        ProductEvent productEvent = (ProductEvent) factory.createEvent(EventType.PRODUCT, Map.of(
                "productId", 77L,
                "action", "INDEXED",
                "timestamp", timestamp,
                "details", Map.of("source", "catalog")
        ));
        assertEquals(77L, productEvent.getProductId());
        assertEquals("INDEXED", productEvent.getAction());
        assertEquals(timestamp, productEvent.getTimestamp());
        assertEquals("catalog", productEvent.getDetails().get("source"));

        OrderEvent orderEvent = (OrderEvent) factory.createEvent(EventType.ORDER, Map.of(
                "orderId", "99",
                "action", "ORDER_CREATED",
                "timestamp", timestamp,
                "details", Map.of("actor", "system")
        ));
        assertEquals(99L, orderEvent.getOrderId());
        assertEquals("ORDER_CREATED", orderEvent.getAction());
        assertEquals(timestamp, orderEvent.getTimestamp());
        assertEquals("system", orderEvent.getDetails().get("actor"));

        ShipmentEvent shipmentEvent = (ShipmentEvent) factory.createEvent(EventType.SHIPMENT, Map.of(
                "shipmentId", 101,
                "action", "TRACKING_RECORDED",
                "timestamp", timestamp,
                "details", Map.of("status", "IN_TRANSIT")
        ));
        assertEquals(101L, shipmentEvent.getShipmentId());
        assertEquals("TRACKING_RECORDED", shipmentEvent.getAction());
        assertEquals(timestamp, shipmentEvent.getTimestamp());
        assertEquals("IN_TRANSIT", shipmentEvent.getDetails().get("status"));

        TransactionAuditEvent auditEvent = (TransactionAuditEvent) factory.createEvent(EventType.TRANSACTION_AUDIT, Map.of(
                "transactionId", 500L,
                "action", "REFUNDED",
                "timestamp", timestamp,
                "method", "CREDIT_CARD",
                "amount", 123.45,
                "details", Map.of("strategy", "PartialItemRefundStrategy")
        ));
        assertEquals(500L, auditEvent.getTransactionId());
        assertEquals("REFUNDED", auditEvent.getAction());
        assertEquals(timestamp, auditEvent.getTimestamp());
        assertEquals("CREDIT_CARD", auditEvent.getMethod());
        assertEquals(123.45, auditEvent.getAmount(), 0.000001);
        assertEquals("PartialItemRefundStrategy", auditEvent.getDetails().get("strategy"));
    }

    @Test
    void createEventAppliesDefaultsAndRejectsNullType() {
        EventFactory factory = new EventFactory();

        AuthEvent authEvent = (AuthEvent) factory.createEvent(EventType.AUTH, null);
        assertNotNull(authEvent.getTimestamp());
        assertNotNull(authEvent.getDetails());
        assertTrue(authEvent.getDetails().isEmpty());

        TransactionAuditEvent auditEvent = (TransactionAuditEvent) factory.createEvent(EventType.TRANSACTION_AUDIT, Map.of(
                "transactionId", 1,
                "action", "REFUNDED"
        ));
        assertNotNull(auditEvent.getTimestamp());
        assertNotNull(auditEvent.getDetails());
        assertTrue(auditEvent.getDetails().isEmpty());
        assertNull(auditEvent.getMethod());
        assertNull(auditEvent.getAmount());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> factory.createEvent(null, Map.of()));
        assertEquals("Event type must not be null", exception.getMessage());
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
