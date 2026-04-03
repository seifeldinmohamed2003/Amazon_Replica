package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;

    public ShipmentService(ShipmentRepository shipmentRepository, JdbcTemplate jdbcTemplate) {
        this.shipmentRepository = shipmentRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Shipment createShipment(Shipment shipment) {
        return shipmentRepository.save(shipment);
    }

    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    public Shipment getShipmentById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    public Shipment updateShipment(Long id, Shipment updatedShipment) {
        Shipment existing = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));

        existing.setCarrier(updatedShipment.getCarrier());
        existing.setStatus(updatedShipment.getStatus());
        existing.setTrackingNumber(updatedShipment.getTrackingNumber());

        return shipmentRepository.save(existing);
    }

    public void deleteShipment(Long id) {
        if (!shipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }
        shipmentRepository.deleteById(id);
    }

    public Shipment getLatestShipmentByOrderId(Long orderId) {
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?",
                Integer.class,
                orderId
        );

        if (orderCount == null || orderCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        return shipmentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No shipment found for this order"));
    }
}