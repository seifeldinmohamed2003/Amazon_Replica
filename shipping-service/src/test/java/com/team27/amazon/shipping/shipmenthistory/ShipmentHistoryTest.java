package com.team27.amazon.shipping.shipmenthistory;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ShipmentHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @BeforeEach
    void setUp() {
        shipmentRepository.deleteAll();
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
    void testGetShipmentsInDateRange_ReturnsThreeShipmentsOrderedByLastUpdate() throws Exception {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // GET /api/shipments/history?startDate=2026-03-01T00:00:00&endDate=2026-03-31T23:59:59
        mockMvc.perform(get("/api/shipments/history")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].trackingNumber", is("TRACK001")))
                .andExpect(jsonPath("$[1].trackingNumber", is("TRACK002")))
                .andExpect(jsonPath("$[2].trackingNumber", is("TRACK003")));
    }

    @Test
    void testGetShipmentsInDateRangeWithStatusFilter_ReturnsTwoDelivered() throws Exception {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // GET /api/shipments/history?startDate=2026-03-01T00:00:00&endDate=2026-03-31T23:59:59&status=DELIVERED
        mockMvc.perform(get("/api/shipments/history")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59")
                        .param("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].trackingNumber", is("TRACK001")))
                .andExpect(jsonPath("$[1].trackingNumber", is("TRACK002")));
    }

    @Test
    void testGetShipmentsInDateRangeWithNoResults_ReturnsEmptyList() throws Exception {
        // Create 5 shipments: 3 in March, 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // GET /api/shipments/history?startDate=2026-04-01T00:00:00&endDate=2026-04-30T23:59:59
        mockMvc.perform(get("/api/shipments/history")
                        .param("startDate", "2026-04-01T00:00:00")
                        .param("endDate", "2026-04-30T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testGetShipmentsInDateRangeWithInTransitStatus_ReturnsOne() throws Exception {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // GET /api/shipments/history?startDate=2026-03-01T00:00:00&endDate=2026-03-31T23:59:59&status=IN_TRANSIT
        mockMvc.perform(get("/api/shipments/history")
                        .param("startDate", "2026-03-01T00:00:00")
                        .param("endDate", "2026-03-31T23:59:59")
                        .param("status", "IN_TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackingNumber", is("TRACK003")));
    }

    @Test
    void testGetShipmentsInDateRangeWithFebruaryRange_ReturnsTwo() throws Exception {
        // Create 5 shipments: 3 in March (2 DELIVERED, 1 IN_TRANSIT), 2 in February
        createShipment(1L, "FedEx", "TRACK001", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 5, 10, 0));
        createShipment(2L, "UPS", "TRACK002", ShipmentStatus.DELIVERED, LocalDateTime.of(2026, 3, 15, 10, 0));
        createShipment(3L, "DHL", "TRACK003", ShipmentStatus.IN_TRANSIT, LocalDateTime.of(2026, 3, 25, 10, 0));
        createShipment(4L, "FedEx", "TRACK004", ShipmentStatus.PROCESSING, LocalDateTime.of(2026, 2, 10, 10, 0));
        createShipment(5L, "UPS", "TRACK005", ShipmentStatus.SHIPPED, LocalDateTime.of(2026, 2, 20, 10, 0));

        // GET /api/shipments/history?startDate=2026-02-01T00:00:00&endDate=2026-02-28T23:59:59
        mockMvc.perform(get("/api/shipments/history")
                        .param("startDate", "2026-02-01T00:00:00")
                        .param("endDate", "2026-02-28T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].trackingNumber", is("TRACK004")))
                .andExpect(jsonPath("$[1].trackingNumber", is("TRACK005")));
    }
}