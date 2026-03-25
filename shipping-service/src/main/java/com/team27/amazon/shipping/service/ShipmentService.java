package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.shipping.dto.CreateShipmentRequest;
import com.team27.amazon.shipping.model.ShipmentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.shipmentRepository = shipmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Shipment createShipment(Shipment shipment) {
        return shipmentRepository.save(shipment);
    }

    public List<Shipment> getAllShipments() {
        return shipmentRepository.findAll();
    }

    public Shipment getShipmentById(Long id) {
        return shipmentRepository.findById(id).orElse(null);
    }

    public Shipment updateShipment(Long id, Shipment updatedShipment) {
        Shipment existing = shipmentRepository.findById(id).orElse(null);

        if (existing == null) return null;

        existing.setCarrier(updatedShipment.getCarrier());
        existing.setStatus(updatedShipment.getStatus());
        existing.setTrackingNumber(updatedShipment.getTrackingNumber());

        return shipmentRepository.save(existing);
    }

    public void deleteShipment(Long id) {
        shipmentRepository.deleteById(id);
    }
    public Shipment getLatestShipmentByOrderId(Long orderId) {
        return shipmentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId).orElse(null);
    }
    public Shipment createShipmentForOrder(Long orderId, CreateShipmentRequest request) {

        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(request.getCarrier());
        shipment.setTrackingNumber(request.getTrackingNumber());
        shipment.setLatitude(request.getLatitude());
        shipment.setLongitude(request.getLongitude());
        shipment.setStatus(ShipmentStatus.PROCESSING);

        try {
            if (request.getMetadata() != null) {
                shipment.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            }
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid metadata format");
        }

        return shipmentRepository.save(shipment);
    }
}