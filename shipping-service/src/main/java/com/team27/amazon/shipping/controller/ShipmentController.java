package com.team27.amazon.shipping.controller;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.service.ShipmentService;
import org.springframework.web.bind.annotation.*;
import com.team27.amazon.shipping.dto.CreateShipmentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;

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
    @PostMapping("/order/{orderId}")
    public ResponseEntity<Shipment> createShipmentForOrder(
            @PathVariable Long orderId,
            @RequestBody CreateShipmentRequest request
    ) {
        Shipment shipment = shipmentService.createShipmentForOrder(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(shipment);
    }


}