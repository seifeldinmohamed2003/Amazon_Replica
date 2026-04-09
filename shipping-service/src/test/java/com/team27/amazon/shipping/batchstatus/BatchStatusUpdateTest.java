package com.team27.amazon.shipping.batchstatus;

import com.team27.amazon.shipping.dto.BatchStatusUpdateRequest;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import com.team27.amazon.shipping.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BatchStatusUpdateTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentService shipmentService;

    private Shipment createShipment(Long orderId, String carrier, String trackingNumber, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(status);
        shipment.setLatitude(0.0);
        shipment.setLongitude(0.0);
        return shipmentRepository.save(shipment);
    }

    @Test
    void testBatchUpdateStatus_SuccessfullyUpdatesThreeShipments() {
        // a) Create 3 shipments with status SHIPPED
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);
        Shipment shipment2 = createShipment(2L, "UPS", "TRACK002", ShipmentStatus.SHIPPED);
        Shipment shipment3 = createShipment(3L, "DHL", "TRACK003", ShipmentStatus.SHIPPED);

        // b) Call batch update to change status to IN_TRANSIT
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 30.0, 31.0));
        requests.add(new BatchStatusUpdateRequest(shipment2.getId(), ShipmentStatus.IN_TRANSIT, 35.0, 36.0));
        requests.add(new BatchStatusUpdateRequest(shipment3.getId(), ShipmentStatus.IN_TRANSIT, 40.0, 41.0));

        // Should return count=3
        int count = shipmentService.batchUpdateStatus(requests);
        assertEquals(3, count);

        // c) Verify all 3 shipments now have status IN_TRANSIT and updated coordinates
        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        Shipment updatedShipment2 = shipmentRepository.findById(shipment2.getId()).orElseThrow();
        Shipment updatedShipment3 = shipmentRepository.findById(shipment3.getId()).orElseThrow();

        assertEquals(ShipmentStatus.IN_TRANSIT, updatedShipment1.getStatus());
        assertEquals(30.0, updatedShipment1.getLatitude());
        assertEquals(31.0, updatedShipment1.getLongitude());
        assertNotNull(updatedShipment1.getLastUpdate());

        assertEquals(ShipmentStatus.IN_TRANSIT, updatedShipment2.getStatus());
        assertEquals(35.0, updatedShipment2.getLatitude());
        assertEquals(36.0, updatedShipment2.getLongitude());
        assertNotNull(updatedShipment2.getLastUpdate());

        assertEquals(ShipmentStatus.IN_TRANSIT, updatedShipment3.getStatus());
        assertEquals(40.0, updatedShipment3.getLatitude());
        assertEquals(41.0, updatedShipment3.getLongitude());
        assertNotNull(updatedShipment3.getLastUpdate());
    }

    @Test
    void testBatchUpdateStatus_WithInvalidLatitude_Throws400() {
        // Create a shipment
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        // d) Test with invalid coordinates (latitude=999)
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 999.0, 31.0));

        // Should throw 400
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            shipmentService.batchUpdateStatus(requests);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Latitude must be between -90 and 90"));

        // Verify shipment was not updated (transactional rollback)
        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        assertEquals(ShipmentStatus.SHIPPED, updatedShipment1.getStatus());
    }

    @Test
    void testBatchUpdateStatus_WithInvalidLongitude_Throws400() {
        // Create a shipment
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        // Test with invalid longitude
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 30.0, 999.0));

        // Should throw 400
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            shipmentService.batchUpdateStatus(requests);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Longitude must be between -180 and 180"));

        // Verify shipment was not updated (transactional rollback)
        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        assertEquals(ShipmentStatus.SHIPPED, updatedShipment1.getStatus());
    }

    @Test
    void testBatchUpdateStatus_WithNonExistentShipmentId_Throws404() {
        // Create a shipment
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        // e) Test with non-existent shipment ID
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 30.0, 31.0));
        requests.add(new BatchStatusUpdateRequest(9999L, ShipmentStatus.IN_TRANSIT, 35.0, 36.0)); // Non-existent

        // Should throw 404
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            shipmentService.batchUpdateStatus(requests);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        // Verify existing shipment was not updated (transactional rollback)
        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        assertEquals(ShipmentStatus.SHIPPED, updatedShipment1.getStatus());
    }

    @Test
    void testBatchUpdateStatus_WithNegativeLatitude_Throws400() {
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, -91.0, 31.0));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            shipmentService.batchUpdateStatus(requests);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Latitude must be between -90 and 90"));
    }

    @Test
    void testBatchUpdateStatus_WithNegativeLongitude_Throws400() {
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 30.0, -181.0));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            shipmentService.batchUpdateStatus(requests);
        });
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Longitude must be between -180 and 180"));
    }

    @Test
    void testBatchUpdateStatus_WithBoundaryCoordinates_Succeeds() {
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        // Test with boundary values (should be valid)
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, 90.0, 180.0));

        int count = shipmentService.batchUpdateStatus(requests);
        assertEquals(1, count);

        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        assertEquals(90.0, updatedShipment1.getLatitude());
        assertEquals(180.0, updatedShipment1.getLongitude());
    }

    @Test
    void testBatchUpdateStatus_WithNegativeBoundaryCoordinates_Succeeds() {
        Shipment shipment1 = createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.SHIPPED);

        // Test with negative boundary values (should be valid)
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        requests.add(new BatchStatusUpdateRequest(shipment1.getId(), ShipmentStatus.IN_TRANSIT, -90.0, -180.0));

        int count = shipmentService.batchUpdateStatus(requests);
        assertEquals(1, count);

        Shipment updatedShipment1 = shipmentRepository.findById(shipment1.getId()).orElseThrow();
        assertEquals(-90.0, updatedShipment1.getLatitude());
        assertEquals(-180.0, updatedShipment1.getLongitude());
    }

    @Test
    void testBatchUpdateStatus_WithEmptyList_ReturnsZero() {
        List<BatchStatusUpdateRequest> requests = new ArrayList<>();
        int count = shipmentService.batchUpdateStatus(requests);
        assertEquals(0, count);
    }
}