package com.team27.amazon.common.events;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObserverPatternTest {

    @Test
    void entityObserverIsInterfaceWithExpectedMethod() {
        assertTrue(EntityObserver.class.isInterface());
        assertNotNull(EntityObserver.class.getDeclaredMethods()[0]);
        assertDoesNotThrow(() -> EntityObserver.class.getDeclaredMethod("onEvent", String.class, Object.class));
    }

    @Test
    void mongoEventLoggerImplementsEntityObserver() {
        assertTrue(EntityObserver.class.isAssignableFrom(MongoEventLogger.class));
        assertDoesNotThrow(() -> MongoEventLogger.class.getDeclaredConstructor(
                EventType.class,
                EventFactory.class,
                java.util.function.Consumer.class
        ));
    }

    @Test
    void subjectRegistersUnregistersAndNotifiesObservers() {
        class TestSubject extends AbstractEventSubject {
        }

        TestSubject subject = new TestSubject();
        AtomicInteger calls = new AtomicInteger();

        EntityObserver observer = (eventType, payload) -> {
            assertEquals("TEST_EVENT", eventType);
            assertEquals("payload", payload);
            calls.incrementAndGet();
        };

        subject.register(observer);
        subject.notifyObservers("TEST_EVENT", "payload");
        assertEquals(1, calls.get());

        subject.unregister(observer);
        subject.notifyObservers("TEST_EVENT", "payload");
        assertEquals(1, calls.get());
    }

    @Test
    void mongoEventLoggerUsesFactoryAndPersistsEvent() {
        AtomicReference<MongoEvent> savedEvent = new AtomicReference<>();
        MongoEventLogger logger = new MongoEventLogger(
                EventType.AUTH,
                new EventFactory(),
                savedEvent::set
        );

        logger.onEvent("REGISTERED", Map.of(
                "userId", 55L,
                "email", "user@example.com",
                "details", Map.of("source", "test")
        ));

        MongoEvent event = savedEvent.get();
        assertInstanceOf(AuthEvent.class, event);

        AuthEvent authEvent = (AuthEvent) event;
        assertEquals(55L, authEvent.getUserId());
        assertEquals("user@example.com", authEvent.getEmail());
        assertEquals("REGISTERED", authEvent.getAction());
        assertEquals("test", authEvent.getDetails().get("source"));
    }

    @Test
    void mongoEventLoggerSwallowsSaveFailures() {
        MongoEventLogger logger = new MongoEventLogger(
                EventType.AUTH,
                new EventFactory(),
                event -> { throw new RuntimeException("mongo down"); }
        );

        assertDoesNotThrow(() -> logger.onEvent("REGISTERED", Map.of("userId", 1L)));
    }
}