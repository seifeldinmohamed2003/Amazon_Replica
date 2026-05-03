package com.team27.amazon.shipping.trackingtimeline;

import com.team27.amazon.shipping.dto.ShipmentTrackingDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;
import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEventKey;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import com.team27.amazon.shipping.repository.TrackingEventRepository;
import com.team27.amazon.shipping.service.ShipmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ShipmentTrackingTimelineTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private TrackingEventRepository trackingEventRepository;

    @Autowired
    private ShipmentService shipmentService;

    private Shipment testShipment;

    @BeforeEach
    void setUp() {
        // Clean up Cassandra tracking events
        trackingEventRepository.deleteAll();
        // Clean up PostgreSQL shipments
        shipmentRepository.deleteAllInBatch();

        // Create a test shipment
        testShipment = new Shipment();
        testShipment.setOrderId(1L);
        testShipment.setCarrier("FedEx");
        testShipment.setTrackingNumber("TRACK123");
        testShipment.setStatus(ShipmentStatus.PROCESSING);
        testShipment = shipmentRepository.save(testShipment);
    }

    @AfterEach
    void tearDown() {
        // Clean up Cassandra tracking events (ignore timeouts)
        try {
            trackingEventRepository.deleteAll();
        } catch (Exception e) {
            // Ignore cleanup errors - Cassandra may be slow in Docker
            System.out.println("Warning: Failed to clean up Cassandra tracking events: " + e.getMessage());
        }
        // Clean up PostgreSQL shipments
        shipmentRepository.deleteAllInBatch();
    }

    private ShipmentTrackingEvent createTrackingEvent(Long shipmentId, LocalDateTime timestamp, String status) {
        ShipmentTrackingEventKey key = new ShipmentTrackingEventKey(shipmentId, timestamp);
        ShipmentTrackingEvent event = new ShipmentTrackingEvent(
                key,
                status,
                "FedEx",
                "TRACK123",
                30.0,
                -90.0,
                "Test note"
        );
        return trackingEventRepository.save(event);
    }

    @Test
    void testGetTrackingTimeline_ReturnsThreeEventsOrderedByMostRecent() {
        // Create 3 tracking events: PROCESSING at 14:00, SHIPPED at 14:15, IN_TRANSIT at 14:30
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 15), "SHIPPED");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 30), "IN_TRANSIT");

        // GET /api/shipments/{id}/tracking - should return 3 events with IN_TRANSIT first
        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), null, null
        );

        assertEquals(3, result.size());
        assertEquals("IN_TRANSIT", result.get(0).getStatus());
        assertEquals("SHIPPED", result.get(1).getStatus());
        assertEquals("PROCESSING", result.get(2).getStatus());
    }

    @Test
    void testGetTrackingTimeline_WithTimeRange_ReturnsOnlyMatchingEvent() {
        // Create 3 tracking events
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 15), "SHIPPED");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 30), "IN_TRANSIT");

        // Query with startTime=2026-04-20T14:10:00&endTime=2026-04-20T14:20:00
        // Should return only the SHIPPED event
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 20, 14, 10);
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 20, 14, 20);

        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), startTime, endTime
        );

        assertEquals(1, result.size());
        assertEquals("SHIPPED", result.get(0).getStatus());
    }

    @Test
    void testGetTrackingTimeline_NonExistentShipment_Throws404() {
        // GET /api/shipments/999/tracking - should return 404
        Long nonExistentId = 999L;

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> shipmentService.getShipmentTrackingTimeline(nonExistentId, null, null)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void testGetTrackingTimeline_NoEvents_ReturnsEmptyList() {
        // Create a new shipment with no tracking events
        Shipment newShipment = new Shipment();
        newShipment.setOrderId(2L);
        newShipment.setCarrier("UPS");
        newShipment.setTrackingNumber("UPS456");
        newShipment.setStatus(ShipmentStatus.PROCESSING); // Required field
        newShipment = shipmentRepository.save(newShipment);

        // Should return empty list
        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                newShipment.getId(), null, null
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetTrackingTimeline_WithOnlyStartTime_ReturnsEventsAfterStartTime() {
        // Create 3 tracking events
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 15), "SHIPPED");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 30), "IN_TRANSIT");

        // Query with startTime=14:10 - should return SHIPPED and IN_TRANSIT
        LocalDateTime startTime = LocalDateTime.of(2026, 4, 20, 14, 10);

        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), startTime, null
        );

        assertEquals(2, result.size());
        assertEquals("IN_TRANSIT", result.get(0).getStatus());
        assertEquals("SHIPPED", result.get(1).getStatus());
    }

    @Test
    void testGetTrackingTimeline_WithOnlyEndTime_ReturnsEventsBeforeEndTime() {
        // Create 3 tracking events
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 15), "SHIPPED");
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 30), "IN_TRANSIT");

        // Query with endTime=14:20 - should return SHIPPED and PROCESSING
        LocalDateTime endTime = LocalDateTime.of(2026, 4, 20, 14, 20);

        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), null, endTime
        );

        assertEquals(2, result.size());
        assertEquals("SHIPPED", result.get(0).getStatus());
        assertEquals("PROCESSING", result.get(1).getStatus());
    }

    @Test
    void testGetTrackingTimeline_DTOContainsAllFields() {
        // Create a tracking event with all fields populated
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");

        List<ShipmentTrackingDTO> result = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), null, null
        );

        assertEquals(1, result.size());
        ShipmentTrackingDTO dto = result.get(0);

        assertEquals(LocalDateTime.of(2026, 4, 20, 14, 0), dto.getTimestamp());
        assertEquals("PROCESSING", dto.getStatus());
        assertEquals("FedEx", dto.getCarrier());
        assertEquals("TRACK123", dto.getTrackingNumber());
        assertEquals(30.0, dto.getLatitude());
        assertEquals(-90.0, dto.getLongitude());
        assertEquals("Test note", dto.getNotes());
    }

    @Test
    void testSaveTrackingEvent_InvalidatesCache() {
        // Create initial tracking event
        createTrackingEvent(testShipment.getId(), LocalDateTime.of(2026, 4, 20, 14, 0), "PROCESSING");

        // First call - should populate cache
        List<ShipmentTrackingDTO> firstResult = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), null, null
        );
        assertEquals(1, firstResult.size());

        // Save a new tracking event using the service method (which invalidates cache)
        ShipmentTrackingEventKey newKey = new ShipmentTrackingEventKey(
                testShipment.getId(),
                LocalDateTime.of(2026, 4, 20, 14, 15)
        );
        ShipmentTrackingEvent newEvent = new ShipmentTrackingEvent(
                newKey,
                "SHIPPED",
                "FedEx",
                "TRACK123",
                31.0,
                -91.0,
                "New event"
        );
        shipmentService.saveTrackingEvent(newEvent);

        // Verify the event was saved by querying directly from repository
        var allEvents = trackingEventRepository.findByShipmentIdOrderByTimestampDesc(testShipment.getId());
        assertEquals(2, allEvents.size());

        // Next call should return updated results (cache was invalidated)
        List<ShipmentTrackingDTO> secondResult = shipmentService.getShipmentTrackingTimeline(
                testShipment.getId(), null, null
        );
        assertEquals(2, secondResult.size());
        assertEquals("SHIPPED", secondResult.get(0).getStatus());
        assertEquals("PROCESSING", secondResult.get(1).getStatus());
    }
}