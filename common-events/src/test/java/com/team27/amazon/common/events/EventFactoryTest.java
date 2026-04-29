package com.team27.amazon.common.events;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
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
    void createEventRejectsNullType() {
        EventFactory factory = new EventFactory();

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
