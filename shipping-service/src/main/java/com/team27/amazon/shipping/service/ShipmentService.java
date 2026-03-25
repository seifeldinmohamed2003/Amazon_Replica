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
import com.team27.amazon.shipping.dto.NearbyShipmentDTO;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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
    public List<NearbyShipmentDTO> findNearbyOutForDelivery(Double lat, Double lon, Double radiusKm) {
        List<Shipment> shipments = shipmentRepository
                .findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ShipmentStatus.OUT_FOR_DELIVERY);

        Map<Long, Shipment> latestPerOrder = new HashMap<>();

        for (Shipment shipment : shipments) {
            Shipment existing = latestPerOrder.get(shipment.getOrderId());

            if (existing == null) {
                latestPerOrder.put(shipment.getOrderId(), shipment);
            } else {
                if (shipment.getLastUpdate() != null && existing.getLastUpdate() != null) {
                    if (shipment.getLastUpdate().isAfter(existing.getLastUpdate())) {
                        latestPerOrder.put(shipment.getOrderId(), shipment);
                    }
                } else if (shipment.getCreatedAt() != null && existing.getCreatedAt() != null) {
                    if (shipment.getCreatedAt().isAfter(existing.getCreatedAt())) {
                        latestPerOrder.put(shipment.getOrderId(), shipment);
                    }
                }
            }
        }

        return latestPerOrder.values().stream()
                .map(shipment -> {
                    double dx = shipment.getLatitude() - lat;
                    double dy = shipment.getLongitude() - lon;
                    double distanceKm = Math.sqrt(dx * dx + dy * dy) * 111.0;

                    return new NearbyShipmentDTO(
                            shipment.getId(),
                            shipment.getOrderId(),
                            shipment.getCarrier(),
                            shipment.getTrackingNumber(),
                            shipment.getLatitude(),
                            shipment.getLongitude(),
                            distanceKm
                    );
                })
                .filter(dto -> dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(NearbyShipmentDTO::getDistanceKm))
                .collect(Collectors.toList());
    }
}