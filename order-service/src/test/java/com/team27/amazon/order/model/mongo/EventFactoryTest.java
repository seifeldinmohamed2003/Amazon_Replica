package com.team27.amazon.order.model.mongo;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    private final EventFactory factory = new EventFactory();

    @Test
    void createEventCreatesOrderEvent() {
        com.team27.amazon.common.events.MongoEvent event = factory.createEvent(EventType.ORDER, Map.of(
                "orderId", "99",
                "action", "ORDER_CREATED",
                "details", Map.of("actor", "system")
        ));

        assertInstanceOf(OrderEvent.class, event);
        assertTrue(MongoEvent.class.isAssignableFrom(OrderEvent.class));

        OrderEvent orderEvent = (OrderEvent) event;
        assertEquals(99L, orderEvent.getOrderId());
        assertEquals("ORDER_CREATED", orderEvent.getAction());
        assertNotNull(orderEvent.getTimestamp());
        assertEquals("system", orderEvent.getDetails().get("actor"));
    }
}
