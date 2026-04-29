package com.team27.amazon.shipping.model.mongo;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    private final EventFactory factory = new EventFactory();

    @Test
    void createEventCreatesShipmentEvent() {
        com.team27.amazon.common.events.MongoEvent event = factory.createEvent(EventType.SHIPMENT, Map.of(
                "shipmentId", 101,
                "action", "TRACKING_RECORDED",
                "details", Map.of("status", "IN_TRANSIT")
        ));

        assertInstanceOf(ShipmentEvent.class, event);
        assertTrue(MongoEvent.class.isAssignableFrom(ShipmentEvent.class));

        ShipmentEvent shipmentEvent = (ShipmentEvent) event;
        assertEquals(101L, shipmentEvent.getShipmentId());
        assertEquals("TRACKING_RECORDED", shipmentEvent.getAction());
        assertNotNull(shipmentEvent.getTimestamp());
        assertEquals("IN_TRANSIT", shipmentEvent.getDetails().get("status"));
    }
}
