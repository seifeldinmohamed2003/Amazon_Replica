package com.team27.amazon.shipping.controller;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
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
}