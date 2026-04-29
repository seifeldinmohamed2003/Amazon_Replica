package com.team27.amazon.product.model.mongo;

import com.team27.amazon.common.events.EventFactory;
import com.team27.amazon.common.events.EventType;
import com.team27.amazon.common.events.MongoEvent;
import com.team27.amazon.common.events.ProductEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventFactoryTest {

    private final EventFactory factory = new EventFactory();

    @Test
    void createEventCreatesProductEvent() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 4, 29, 12, 0);
        MongoEvent event = factory.createEvent(EventType.PRODUCT, Map.of(
                "productId", 77L,
                "action", "INDEXED",
                "timestamp", timestamp,
                "details", Map.of("source", "explicit")
        ));

        assertInstanceOf(ProductEvent.class, event);

        ProductEvent productEvent = (ProductEvent) event;
        assertEquals(77L, productEvent.getProductId());
        assertEquals("INDEXED", productEvent.getAction());
        assertEquals(timestamp, productEvent.getTimestamp());
        assertEquals("explicit", productEvent.getDetails().get("source"));
    }
}
