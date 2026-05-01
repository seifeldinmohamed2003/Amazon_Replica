package com.team27.amazon.shipping.shipmenthistory;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ShipmentHistoryTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @BeforeEach
    void cleanDatabaseBeforeEachTest() {
        shipmentRepository.deleteAllInBatch();
    }

    @AfterEach
    void cleanDatabaseAfterEachTest() {
        shipmentRepository.deleteAllInBatch();
    }

    private Shipment createShipment(Long orderId, String carrier, String trackingNumber, ShipmentStatus status, LocalDateTime lastUpdate) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(status);
        shipment.setLastUpdate(lastUpdate);
        shipment.setCreatedAt(lastUpdate.minusDays(5));
        return shipmentRepository.save(shipment);
    }

    @Test
    void testGetShipmentsInDateRange_ReturnsThreeShipmentsOrderedByLastUpdate() {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // Query shipments in March date range
        LocalDateTime startDate = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 3, 31, 23, 59, 59);
        List<Shipment> shipments = shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, null);

        // Should return 3 shipments ordered by lastUpdate ascending
        assertEquals(3, shipments.size());
        assertEquals("TRACK001", shipments.get(0).getTrackingNumber());
        assertEquals("TRACK002", shipments.get(1).getTrackingNumber());
        assertEquals("TRACK003", shipments.get(2).getTrackingNumber());
    }

    @Test
    void testGetShipmentsInDateRangeWithStatusFilter_ReturnsTwoDelivered() {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // Query shipments in March with DELIVERED status
        LocalDateTime startDate = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 3, 31, 23, 59, 59);
        List<Shipment> shipments = shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, ShipmentStatus.DELIVERED);

        // Should return 2 DELIVERED shipments
        assertEquals(2, shipments.size());
        assertEquals("TRACK001", shipments.get(0).getTrackingNumber());
        assertEquals("TRACK002", shipments.get(1).getTrackingNumber());
    }

    @Test
    void testGetShipmentsInDateRangeWithNoResults_ReturnsEmptyList() {
        // Create 5 shipments: 3 in March, 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // Query shipments in April (no shipments exist)
        LocalDateTime startDate = LocalDateTime.of(2026, 4, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 4, 30, 23, 59, 59);
        List<Shipment> shipments = shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, null);

        // Should return empty list
        assertTrue(shipments.isEmpty());
    }

    @Test
    void testGetShipmentsInDateRangeWithInTransitStatus_ReturnsOne() {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // Query shipments in March with IN_TRANSIT status
        LocalDateTime startDate = LocalDateTime.of(2026, 3, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 3, 31, 23, 59, 59);
        List<Shipment> shipments = shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, ShipmentStatus.IN_TRANSIT);

        // Should return 1 IN_TRANSIT shipment
        assertEquals(1, shipments.size());
        assertEquals("TRACK003", shipments.get(0).getTrackingNumber());
    }

    @Test
    void testGetShipmentsInDateRangeWithFebruaryRange_ReturnsTwo() {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // Query shipments in February
        LocalDateTime startDate = LocalDateTime.of(2026, 2, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 2, 28, 23, 59, 59);
        List<Shipment> shipments = shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, null);

        // Should return 2 shipments ordered by lastUpdate ascending
        assertEquals(2, shipments.size());
        assertEquals("TRACK004", shipments.get(0).getTrackingNumber());
        assertEquals("TRACK005", shipments.get(1).getTrackingNumber());
    }
}