package com.team27.amazon.shipping.controller;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.CreateShipmentRequest;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.dto.NearbyShipmentDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.service.ShipmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public Shipment createShipment(@RequestBody Shipment shipment) {
        return shipmentService.createShipment(shipment);
    }

    @PostMapping("/order/{orderId}")
    public ResponseEntity<Shipment> createShipmentForOrder(
            @PathVariable Long orderId,
            @RequestBody CreateShipmentRequest request
    ) {
        Shipment shipment = shipmentService.createShipmentForOrder(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
    }

    @GetMapping
    public List<Shipment> getAllShipments() {
        return shipmentService.getAllShipments();
    }

    @GetMapping("/{id}")
    public Shipment getShipment(@PathVariable Long id) {
        return shipmentService.getShipmentById(id);
    }

    @PutMapping("/{id}")
    public Shipment updateShipment(@PathVariable Long id, @RequestBody Shipment shipment) {
        return shipmentService.updateShipment(id, shipment);
    }

    @DeleteMapping("/{id}")
    public void deleteShipment(@PathVariable Long id) {
        shipmentService.deleteShipment(id);
    }

    @GetMapping("/order/{orderId}/latest")
    public Shipment getLatestShipmentByOrderId(@PathVariable Long orderId) {
        return shipmentService.getLatestShipmentByOrderId(orderId);
    }

    @GetMapping("/nearby")
    public List<NearbyShipmentDTO> findNearbyShipments(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam Double radiusKm
    ) {
        return shipmentService.findNearbyOutForDelivery(lat, lon, radiusKm);
    }

    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Integer>> purgeOldShipments(
            @RequestParam int olderThanDays) {
        int deletedCount = shipmentService.purgeOldShipments(olderThanDays);
        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }

    @GetMapping("/carrier/{carrier}/summary")
    public ResponseEntity<CarrierSummaryDTO> getCarrierSummary(
            @PathVariable String carrier,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(
                shipmentService.getCarrierSummary(carrier, startDate, endDate)
        );
    }

    @GetMapping("/delayed")
    public ResponseEntity<List<DelayedShipmentDTO>> getDelayedShipments(
            @RequestParam(required = false) Integer maxDeliveryAttempts) {
        return ResponseEntity.ok(
                shipmentService.getDelayedShipments(maxDeliveryAttempts)
        );
    }

    @GetMapping("/history")
    public ResponseEntity<List<Shipment>> getShipmentsInDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            @RequestParam(required = false) ShipmentStatus status
    ) {
        return ResponseEntity.ok(
                shipmentService.getShipmentsInDateRange(startDate, endDate, status)
        );
    }

    @GetMapping("/metadata/search")
    public ResponseEntity<List<Shipment>> searchShipmentsByMetadata(
            @RequestParam String key,
            @RequestParam String operator,
            @RequestParam String value
    ) {
        return ResponseEntity.ok(
                shipmentService.searchShipmentsByMetadata(key, operator, value)
        );
    }
}
