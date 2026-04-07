package com.team27.amazon.shipping.controller;

import com.team27.amazon.shipping.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @DeleteMapping("/purge")
    public ResponseEntity<Map<String, Integer>> purgeOldShipments(
            @RequestParam int olderThanDays) {

        int deletedCount = shipmentService.purgeOldShipments(olderThanDays);

        return ResponseEntity.ok(Map.of("deletedCount", deletedCount));
    }
}