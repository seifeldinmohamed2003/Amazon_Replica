package com.team27.amazon.shipping.controller;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.service.ShipmentService;
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
}