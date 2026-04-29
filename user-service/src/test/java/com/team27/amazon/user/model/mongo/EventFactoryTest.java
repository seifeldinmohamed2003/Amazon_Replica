package com.team27.amazon.user.model.mongo;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    private final EventFactory factory = new EventFactory();

    @Test
    void createEventCreatesAuthEventAndAppliesDefaults() {
        com.team27.amazon.common.events.MongoEvent event = factory.createEvent(EventType.AUTH, Map.of(
                "userId", 15,
                "email", "auth@example.com",
                "action", "REGISTERED"
        ));

        assertInstanceOf(AuthEvent.class, event);
        assertTrue(MongoEvent.class.isAssignableFrom(AuthEvent.class));

        AuthEvent authEvent = (AuthEvent) event;
        assertEquals(15L, authEvent.getUserId());
        assertEquals("auth@example.com", authEvent.getEmail());
        assertEquals("REGISTERED", authEvent.getAction());
        assertNotNull(authEvent.getTimestamp());
        assertNotNull(authEvent.getDetails());
        assertTrue(authEvent.getDetails().isEmpty());
    }

    @Test
    void createEventWithNullParamsStillAppliesDefaults() {
        com.team27.amazon.common.events.MongoEvent event = factory.createEvent(EventType.AUTH, null);

        assertInstanceOf(AuthEvent.class, event);

        AuthEvent authEvent = (AuthEvent) event;
        assertNotNull(authEvent.getTimestamp());
        assertNotNull(authEvent.getDetails());
        assertTrue(authEvent.getDetails().isEmpty());
        assertNull(authEvent.getUserId());
        assertNull(authEvent.getEmail());
        assertNull(authEvent.getAction());
    }
}
