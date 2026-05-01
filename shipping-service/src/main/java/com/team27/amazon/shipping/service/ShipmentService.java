package com.team27.amazon.shipping.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.shipping.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.shipping.dto.BatchStatusUpdateRequest;
import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.CreateShipmentRequest;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.dto.NearbyShipmentDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;

import jakarta.annotation.PostConstruct;

@Service
public class ShipmentService extends AbstractEventSubject {

    private final ShipmentRepository shipmentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @Autowired
    @Qualifier("shipmentEventLogger")
    private MongoEventLogger mongoEventLogger;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectArrayDtoAdapter objectArrayDtoAdapter

    ) {
        this.shipmentRepository = shipmentRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;

    }

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    public Shipment createShipment(Shipment shipment) {
        Shipment savedShipment = shipmentRepository.save(shipment);
        notifyObservers("SHIPMENT_CREATED", shipmentEventPayload(savedShipment.getId(), Map.of(
                "orderId", savedShipment.getOrderId(),
                "status", savedShipment.getStatus() == null ? null : savedShipment.getStatus().name()
        )));
        return savedShipment;
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

        existing.setOrderId(updatedShipment.getOrderId());
        existing.setCarrier(updatedShipment.getCarrier());
        existing.setTrackingNumber(updatedShipment.getTrackingNumber());
        existing.setStatus(updatedShipment.getStatus());
        existing.setLatitude(updatedShipment.getLatitude());
        existing.setLongitude(updatedShipment.getLongitude());
        existing.setEstimatedDelivery(updatedShipment.getEstimatedDelivery());
        existing.setActualDelivery(updatedShipment.getActualDelivery());
        existing.setMetadata(updatedShipment.getMetadata());

        Shipment savedShipment = shipmentRepository.save(existing);
        notifyObservers("SHIPMENT_UPDATED", shipmentEventPayload(savedShipment.getId(), Map.of(
            "orderId", savedShipment.getOrderId(),
            "status", savedShipment.getStatus() == null ? null : savedShipment.getStatus().name()
        )));
        return savedShipment;
    }

    public void deleteShipment(Long id) {
        if (!shipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }
        shipmentRepository.deleteById(id);
        notifyObservers("SHIPMENT_DELETED", shipmentEventPayload(id, Map.of()));
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

    public Shipment createShipmentForOrder(Long orderId, CreateShipmentRequest request) {
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?",
                Integer.class,
                orderId
        );

        if (orderCount == null || orderCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        Shipment shipment = new Shipment();
        shipment.setOrderId(orderId);
        shipment.setCarrier(request.getCarrier());
        shipment.setTrackingNumber(request.getTrackingNumber());
        shipment.setLatitude(request.getLatitude());
        shipment.setLongitude(request.getLongitude());
        shipment.setStatus(ShipmentStatus.PROCESSING);

        if (request.getMetadata() != null) {
            shipment.setMetadata(request.getMetadata());
        }

        Shipment savedShipment = shipmentRepository.save(shipment);
        notifyObservers("SHIPMENT_CREATED", shipmentEventPayload(savedShipment.getId(), Map.of(
                "orderId", savedShipment.getOrderId(),
                "status", savedShipment.getStatus() == null ? null : savedShipment.getStatus().name()
        )));
        return savedShipment;
    }

    public List<NearbyShipmentDTO> findNearbyOutForDelivery(Double lat, Double lon, Double radiusKm) {
        if (lat == null || lon == null || radiusKm == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat, lon and radiusKm are required");
        }

        if (radiusKm < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be >= 0");
        }

        List<Shipment> shipments = shipmentRepository
                .findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(ShipmentStatus.OUT_FOR_DELIVERY);

        Map<Long, Shipment> latestPerOrder = new HashMap<>();

        for (Shipment shipment : shipments) {
            Shipment existing = latestPerOrder.get(shipment.getOrderId());

            if (existing == null) {
                latestPerOrder.put(shipment.getOrderId(), shipment);
                continue;
            }

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

        return latestPerOrder.values().stream()
                .map(shipment -> {
                    double dx = shipment.getLatitude() - lat;
                    double dy = shipment.getLongitude() - lon;
                    double distanceKm = Math.sqrt(dx * dx + dy * dy) * 111.0;

                    return NearbyShipmentDTO.builder()
                            .shipmentId(shipment.getId())
                            .orderId(shipment.getOrderId())
                            .carrier(shipment.getCarrier())
                            .trackingNumber(shipment.getTrackingNumber())
                            .latitude(shipment.getLatitude())
                            .longitude(shipment.getLongitude())
                            .distanceKm(distanceKm)
                            .build();
                })
                .filter(dto -> dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(NearbyShipmentDTO::getDistanceKm))
                .collect(Collectors.toList());
    }

    @Transactional
    public int purgeOldShipments(int olderThanDays) {
        if (olderThanDays < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "olderThanDays must be non-negative"
            );
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(olderThanDays);
        int count = shipmentRepository.countOlderThan(cutoff);
        shipmentRepository.deleteOlderThan(cutoff);
        notifyObservers("OLD_DATA_PURGED", shipmentEventPayload(null, Map.of(
            "olderThanDays", olderThanDays,
            "deletedCount", count
        )));
        return count;
    }

    public CarrierSummaryDTO getCarrierSummary(String carrier,
                                               LocalDateTime start,
                                               LocalDateTime end) {

        if (start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate"
            );
        }

        List<Shipment> shipments = shipmentRepository.findByCarrierAndDateRange(carrier, start, end);

        if (shipments.isEmpty()) {
            return CarrierSummaryDTO.builder()
                    .carrier(carrier)
                    .totalShipments(0)
                    .deliveredCount(0)
                    .averageDeliveryDays(0.0)
                    .onTimeRate(0.0)
                    .build();}

        long totalShipments = shipments.size();

        long deliveredCount = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED)
                .count();

        long onTimeCount = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED)
                .filter(s -> s.getActualDelivery() != null && s.getEstimatedDelivery() != null)
                .filter(s -> !s.getActualDelivery().isAfter(s.getEstimatedDelivery()))
                .count();

        double onTimeRate = deliveredCount == 0
                ? 0.0
                : (onTimeCount * 100.0) / deliveredCount;

        double averageDeliveryDays = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED)
                .filter(s -> s.getActualDelivery() != null && s.getCreatedAt() != null)
                .mapToLong(s -> ChronoUnit.DAYS.between(
                        s.getCreatedAt().toLocalDate(),
                        s.getActualDelivery()
                ))
                .average()
                .orElse(0.0);

        return CarrierSummaryDTO.builder()
                .carrier(carrier)
                .totalShipments(totalShipments)
                .deliveredCount(deliveredCount)
                .averageDeliveryDays(averageDeliveryDays)
                .onTimeRate(onTimeRate)
                .build();
    }

    public List<DelayedShipmentDTO> getDelayedShipments(Integer maxDeliveryAttempts) {
        if (maxDeliveryAttempts != null && maxDeliveryAttempts < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxDeliveryAttempts must be non-negative"
            );
        }

        List<Object[]> rows = shipmentRepository.findDelayedShipments(maxDeliveryAttempts);

       return rows.stream()
        .map(objectArrayDtoAdapter::toDelayedShipmentDTO)
        .toList();
    }

    public List<Shipment> getShipmentsInDateRange(LocalDateTime startDate, LocalDateTime endDate, ShipmentStatus status) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate and endDate are required"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "startDate cannot be after endDate"
            );
        }

        return shipmentRepository.findShipmentsByDateRangeAndStatus(startDate, endDate, status);
    }

    public List<Shipment> searchShipmentsByMetadata(String key, String operator, String value) {
        if (key == null || key.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "key parameter is required"
            );
        }

        if (operator == null || operator.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "operator parameter is required"
            );
        }

        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "value parameter is required"
            );
        }

        String normalizedOperator = operator.trim().toLowerCase();

        switch (normalizedOperator) {
            case "eq":
                return shipmentRepository.findByMetadataKeyAndValueEquals(key, value);
            case "gt":
                return shipmentRepository.findByMetadataKeyAndValueGreaterThan(key, value);
            case "lt":
                return shipmentRepository.findByMetadataKeyAndValueLessThan(key, value);
            default:
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid operator. Supported operators are: eq, gt, lt"
                );
        }
    }

    @Transactional
    public int batchUpdateStatus(List<BatchStatusUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return 0;
        }

        // Extract all shipment IDs and validate all shipments exist
        List<Long> shipmentIds = requests.stream()
                .map(BatchStatusUpdateRequest::getShipmentId)
                .toList();

        List<Shipment> existingShipments = shipmentRepository.findAllById(shipmentIds);
        if (existingShipments.size() != shipmentIds.size()) {
            // Find which shipment IDs are missing
            List<Long> existingIds = existingShipments.stream()
                    .map(Shipment::getId)
                    .toList();
            List<Long> missingIds = shipmentIds.stream()
                    .filter(id -> !existingIds.contains(id))
                    .toList();
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Shipments not found with ids: " + missingIds
            );
        }

        // Validate coordinates for all requests
        for (BatchStatusUpdateRequest request : requests) {
            validateCoordinates(request.getLatitude(), request.getLongitude());
        }

        // Update each shipment
        for (BatchStatusUpdateRequest request : requests) {
            Shipment shipment = existingShipments.stream()
                    .filter(s -> s.getId().equals(request.getShipmentId()))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Shipment not found with id: " + request.getShipmentId()
                    ));

            shipment.setStatus(request.getStatus());
            shipment.setLatitude(request.getLatitude());
            shipment.setLongitude(request.getLongitude());
            // lastUpdate will be automatically set by @PreUpdate
        }

        // Save all updated shipments
        shipmentRepository.saveAll(existingShipments);

        notifyObservers("BATCH_STATUS_UPDATED", shipmentEventPayload(null, Map.of(
                "count", existingShipments.size(),
                "shipmentIds", shipmentIds
        )));

        return existingShipments.size();
    }

    private Map<String, Object> shipmentEventPayload(Long shipmentId, Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        if (shipmentId != null) {
            payload.put("shipmentId", shipmentId);
        }
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Latitude and longitude are required"
            );
        }

        if (latitude < -90 || latitude > 90) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Latitude must be between -90 and 90"
            );
        }

        if (longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Longitude must be between -180 and 180"
            );
        }
    }
}
