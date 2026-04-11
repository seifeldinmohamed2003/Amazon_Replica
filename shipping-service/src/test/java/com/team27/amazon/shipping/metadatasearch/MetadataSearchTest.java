package com.team27.amazon.shipping.metadatasearch;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MetadataSearchTest {

    @Autowired
    private ShipmentRepository shipmentRepository;

    private Shipment createShipmentWithMetadata(Long orderId, String carrier, String trackingNumber, Map<String, Object> metadata) {
        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(carrier);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setStatus(ShipmentStatus.PROCESSING);
        shipment.setMetadata(metadata != null ? metadata : new HashMap<>());
        return shipmentRepository.save(shipment);
    }

    @Test
    void testSearchByMetadata_EqualsOperator_ReturnsOneShipment() {
        // Create shipments with metadata: weight=1.5, weight=3.0, weight=5.0
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("weight", 3.0);
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("weight", 5.0);
        createShipmentWithMetadata(3L, "DHL", "TRACK003", metadata3);

        // Query: GET /api/shipments/metadata/search?key=weight&operator=eq&value=1.5
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueEquals("weight", "1.5");

        // Should return 1 shipment with weight=1.5
        assertEquals(1, shipments.size());
        assertEquals("TRACK001", shipments.get(0).getTrackingNumber());
    }

    @Test
    void testSearchByMetadata_GreaterThanOperator_ReturnsTwoShipments() {
        // Create shipments with metadata: weight=1.5, weight=3.0, weight=5.0
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("weight", 3.0);
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("weight", 5.0);
        createShipmentWithMetadata(3L, "DHL", "TRACK003", metadata3);

        // Query: GET /api/shipments/metadata/search?key=weight&operator=gt&value=2.0
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueGreaterThan("weight", "2.0");

        // Should return 2 shipments with weight > 2.0 (3.0 and 5.0)
        assertEquals(2, shipments.size());
        List<String> trackingNumbers = shipments.stream()
                .map(Shipment::getTrackingNumber)
                .toList();
        assertTrue(trackingNumbers.contains("TRACK002"));
        assertTrue(trackingNumbers.contains("TRACK003"));
    }

    @Test
    void testSearchByMetadata_LessThanOperator_ReturnsOneShipment() {
        // Create shipments with metadata: weight=1.5, weight=3.0, weight=5.0
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("weight", 3.0);
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("weight", 5.0);
        createShipmentWithMetadata(3L, "DHL", "TRACK003", metadata3);

        // Query: GET /api/shipments/metadata/search?key=weight&operator=lt&value=2.0
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueLessThan("weight", "2.0");

        // Should return 1 shipment with weight < 2.0 (1.5)
        assertEquals(1, shipments.size());
        assertEquals("TRACK001", shipments.get(0).getTrackingNumber());
    }

    @Test
    void testSearchByMetadata_NoMatches_ReturnsEmptyList() {
        // Create shipments with metadata: weight=1.5, weight=3.0, weight=5.0
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("weight", 3.0);
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("weight", 5.0);
        createShipmentWithMetadata(3L, "DHL", "TRACK003", metadata3);

        // Query: GET /api/shipments/metadata/search?key=weight&operator=eq&value=10.0
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueEquals("weight", "10.0");

        // Should return empty list
        assertTrue(shipments.isEmpty());
    }

    @Test
    void testSearchByMetadata_NonExistentKey_ReturnsEmptyList() {
        // Create shipment with metadata: weight=1.5
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        // Query: GET /api/shipments/metadata/search?key=height&operator=eq&value=1.5
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueEquals("height", "1.5");

        // Should return empty list
        assertTrue(shipments.isEmpty());
    }

    @Test
    void testSearchByMetadata_LessThanOperator_ReturnsTwoShipments() {
        // Create shipments with metadata: weight=1.5, weight=3.0, weight=5.0
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("weight", 1.5);
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("weight", 3.0);
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        Map<String, Object> metadata3 = new HashMap<>();
        metadata3.put("weight", 5.0);
        createShipmentWithMetadata(3L, "DHL", "TRACK003", metadata3);

        // Query: GET /api/shipments/metadata/search?key=weight&operator=lt&value=4.0
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueLessThan("weight", "4.0");

        // Should return 2 shipments with weight < 4.0 (1.5 and 3.0)
        assertEquals(2, shipments.size());
        List<String> trackingNumbers = shipments.stream()
                .map(Shipment::getTrackingNumber)
                .toList();
        assertTrue(trackingNumbers.contains("TRACK001"));
        assertTrue(trackingNumbers.contains("TRACK002"));
    }

    @Test
    void testSearchByMetadata_EqualsOperator_WithStringValue() {
        // Create shipments with string metadata
        Map<String, Object> metadata1 = new HashMap<>();
        metadata1.put("color", "red");
        createShipmentWithMetadata(1L, "FedEx", "TRACK001", metadata1);

        Map<String, Object> metadata2 = new HashMap<>();
        metadata2.put("color", "blue");
        createShipmentWithMetadata(2L, "UPS", "TRACK002", metadata2);

        // Query: GET /api/shipments/metadata/search?key=color&operator=eq&value=red
        List<Shipment> shipments = shipmentRepository.findByMetadataKeyAndValueEquals("color", "red");

        // Should return 1 shipment with color=red
        assertEquals(1, shipments.size());
        assertEquals("TRACK001", shipments.get(0).getTrackingNumber());
    }
}