package com.team27.amazon.shipping.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;
import com.team27.amazon.shipping.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.shipping.config.RedisConfiguration;
import com.team27.amazon.shipping.dto.BatchStatusUpdateRequest;
import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.CreateShipmentRequest;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.dto.NearbyShipmentDTO;
import com.team27.amazon.shipping.dto.ShippingAnalyticsDTO;
import com.team27.amazon.shipping.dto.ShipmentTrackingDTO;
import com.team27.amazon.shipping.dto.TrackingEventRequest;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.model.cassandra.ShipmentTrackingEvent;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import com.team27.amazon.shipping.repository.ShipmentTrackingEventRepository;

import jakarta.annotation.PostConstruct;

@Service
public class ShipmentService extends AbstractEventSubject {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentTrackingEventRepository shipmentTrackingEventRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @Autowired
    @Qualifier("shipmentEventLogger")
    private MongoEventLogger mongoEventLogger;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            ShipmentTrackingEventRepository shipmentTrackingEventRepository,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectArrayDtoAdapter objectArrayDtoAdapter
    ) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentTrackingEventRepository = shipmentTrackingEventRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
    }

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
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

    // GET /{id} - CRUD baseline endpoint - TTL 15 min
    // Cache key: shipping-service::shipment::{id}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, key = "#id")
    public Shipment getShipmentById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, key = "#id"),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
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

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, key = "#id"),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
    public void deleteShipment(Long id) {
        if (!shipmentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }
        shipmentRepository.deleteById(id);
        notifyObservers("SHIPMENT_DELETED", shipmentEventPayload(id, Map.of()));
    }

    // F1: Get Latest Shipment for an Order - TTL 5 min
    // Cache key: shipping-service::S4-F1::{orderId}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F1, key = "#orderId")
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

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
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

    // S4-F11: Record Shipment Tracking Event
    // Endpoint: POST /api/shipments/{id}/tracking
    // Cassandra: shipment_tracking_events
    // MongoDB Observer event: TRACKING_RECORDED
    @Caching(evict = {
            @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true),
            @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F12, allEntries = true)
    })
    public ShipmentTrackingEvent recordTrackingEvent(Long shipmentId, TrackingEventRequest request) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking event request body is required");
        }

        if (request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        validateCoordinates(request.getLatitude(), request.getLongitude());

        LocalDateTime now = LocalDateTime.now();

        ShipmentTrackingEvent trackingEvent = new ShipmentTrackingEvent(
                shipmentId,
                now,
                request.getStatus(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                request.getLatitude(),
                request.getLongitude(),
                request.getNotes()
        );

        ShipmentTrackingEvent savedEvent = shipmentTrackingEventRepository.save(trackingEvent);

        Map<String, Object> details = new HashMap<>();
        details.put("shipmentId", shipmentId);
        details.put("status", request.getStatus());
        details.put("latitude", request.getLatitude());
        details.put("longitude", request.getLongitude());
        details.put("notes", request.getNotes());
        details.put("carrier", shipment.getCarrier());
        details.put("trackingNumber", shipment.getTrackingNumber());
        details.put("timestamp", now.toString());

        notifyObservers("TRACKING_RECORDED", shipmentEventPayload(shipmentId, details));

        return savedEvent;
    }

    // F3: Find Nearby Shipments Out for Delivery - TTL 10 min
    // Cache key: shipping-service::S4-F3::{lat}:{lon}:{radiusKm}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F3,
               key = "#lat + ':' + #lon + ':' + #radiusKm")
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

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
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

    // F8: Carrier Performance Summary - TTL 15 min
    // Cache key: shipping-service::S4-F8::{carrier}:{start}:{end}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F8,
               key = "#carrier + ':' + #start + ':' + #end")
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
                    .build();
        }

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

    // F9: Find Delayed Shipments - TTL 10 min
    // Cache key: shipping-service::S4-F9::{maxDeliveryAttempts}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F9,
               key = "#maxDeliveryAttempts != null ? #maxDeliveryAttempts : 'all'")
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

    // F6: Get Shipments in Date Range - TTL 10 min
    // Cache key: shipping-service::S4-F6::{startDate}:{endDate}:{status}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F6,
               key = "#startDate + ':' + #endDate + ':' + (#status != null ? #status.name() : 'all')")
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

    // F5: Filter Shipments by Metadata (JSONB Query) - TTL 5 min
    // Cache key: shipping-service::S4-F5::{key}:{operator}:{value}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F5,
               key = "#key + ':' + #operator + ':' + #value")
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

    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_SHIPMENT_DETAIL, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F1, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F3, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F5, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F6, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F8, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F9, allEntries = true),
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F10, allEntries = true)
    })
    @Transactional
    public int batchUpdateStatus(List<BatchStatusUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return 0;
        }

        List<Long> shipmentIds = requests.stream()
                .map(BatchStatusUpdateRequest::getShipmentId)
                .toList();

        List<Shipment> existingShipments = shipmentRepository.findAllById(shipmentIds);
        if (existingShipments.size() != shipmentIds.size()) {
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

        for (BatchStatusUpdateRequest request : requests) {
            validateCoordinates(request.getLatitude(), request.getLongitude());
        }

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
        }

        shipmentRepository.saveAll(existingShipments);

        notifyObservers("BATCH_STATUS_UPDATED", shipmentEventPayload(null, Map.of(
                "count", existingShipments.size(),
                "shipmentIds", shipmentIds
        )));

        return existingShipments.size();
    }

    // F12: Get Shipment Tracking Timeline - TTL 5 min
    // Cache key: shipping-service::S4-F12::{shipmentId}:{startTime}:{endTime}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F12,
               key = "#shipmentId + ':' + (#startTime != null ? #startTime.toString() : 'all') + ':' + (#endTime != null ? #endTime.toString() : 'all')")
    public List<ShipmentTrackingDTO> getShipmentTrackingTimeline(
            Long shipmentId,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        // Verify shipment exists in PostgreSQL
        if (!shipmentRepository.existsById(shipmentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found");
        }

        List<ShipmentTrackingEvent> events;

        if (startTime != null && endTime != null) {
            events = shipmentTrackingEventRepository.findByShipmentIdAndTimestampBetweenOrderByTimestampDesc(
                    shipmentId, startTime, endTime
            );
        } else if (startTime != null) {
            events = shipmentTrackingEventRepository.findByShipmentIdAndTimestampAfterOrderByTimestampDesc(
                    shipmentId, startTime
            );
        } else if (endTime != null) {
            events = shipmentTrackingEventRepository.findByShipmentIdAndTimestampBeforeOrderByTimestampDesc(
                    shipmentId, endTime
            );
        } else {
            events = shipmentTrackingEventRepository.findByShipmentIdOrderByTimestampDesc(shipmentId);
        }

        return events.stream()
                .map(event -> ShipmentTrackingDTO.builder()
                        .timestamp(event.getTimestamp())
                        .status(event.getStatus())
                        .carrier(event.getCarrier())
                        .trackingNumber(event.getTrackingNumber())
                        .latitude(event.getLatitude())
                        .longitude(event.getLongitude())
                        .notes(event.getNotes())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Save a tracking event for a shipment (used by S4-F11).
     * This method also invalidates the S4-F12 cache for this shipment.
     */
    @Caching(evict = {
        @CacheEvict(cacheNames = RedisConfiguration.CACHE_S4_F12, allEntries = true)
    })
    public ShipmentTrackingEvent saveTrackingEvent(ShipmentTrackingEvent event) {
        return shipmentTrackingEventRepository.save(event);
    }


    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDate localDate) {
            return localDate;
        }

        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }

        throw new IllegalArgumentException("Unsupported date value type: " + value.getClass().getName());
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

    public void logShippingAnalyticsViewed(LocalDate startDate, LocalDate endDate) {
        notifyObservers("ANALYTICS_VIEWED", shipmentEventPayload(null, Map.of(
                "feature", "S4-F10",
                "startDate", startDate == null ? null : startDate.toString(),
                "endDate", endDate == null ? null : endDate.toString()
        )));
    }

    // S4-F10: Get Shipping Analytics Dashboard - TTL 10 min
    // Cache key: shipping-service::S4-F10::{startDate}:{endDate}
    @Cacheable(cacheNames = RedisConfiguration.CACHE_S4_F10,
               key = "#startDate.toString() + ':' + #endDate.toString()")
    public ShippingAnalyticsDTO getShippingAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59, 999_000_000);

        List<Shipment> shipments = shipmentRepository.findByCreatedAtBetween(start, end);

        long totalShipments = shipments.size();

        Map<String, Long> shipmentsByStatus = shipments.stream()
                .filter(shipment -> shipment.getStatus() != null)
                .collect(Collectors.groupingBy(
                        shipment -> shipment.getStatus().name(),
                        Collectors.counting()
                ));

        List<Shipment> deliveredShipments = shipments.stream()
                .filter(shipment -> shipment.getStatus() == ShipmentStatus.DELIVERED)
                .toList();

        double averageDeliveryTimeDays = deliveredShipments.stream()
                .filter(shipment -> shipment.getActualDelivery() != null && shipment.getCreatedAt() != null)
                .mapToLong(shipment -> ChronoUnit.DAYS.between(
                        shipment.getCreatedAt().toLocalDate(),
                        shipment.getActualDelivery()
                ))
                .average()
                .orElse(0.0);

        long onTimeDeliveredCount = deliveredShipments.stream()
                .filter(shipment -> shipment.getActualDelivery() != null && shipment.getEstimatedDelivery() != null)
                .filter(shipment -> !shipment.getActualDelivery().isAfter(shipment.getEstimatedDelivery()))
                .count();

        double onTimeRate = deliveredShipments.isEmpty()
                ? 0.0
                : (double) onTimeDeliveredCount / deliveredShipments.size();

        double averageAttempts = deliveredShipments.stream()
                .mapToDouble(this::extractDeliveryAttempts)
                .average()
                .orElse(0.0);

        return ShippingAnalyticsDTO.builder()
                .totalShipments(totalShipments)
                .averageDeliveryTimeDays(averageDeliveryTimeDays)
                .onTimeRate(onTimeRate)
                .shipmentsByStatus(shipmentsByStatus)
                .averageAttempts(averageAttempts)
                .build();
    }

    private double extractDeliveryAttempts(Shipment shipment) {
        if (shipment == null || shipment.getMetadata() == null) {
            return 0.0;
        }

        Object attempts = shipment.getMetadata().get("deliveryAttempts");
        if (attempts == null) {
            return 0.0;
        }

        if (attempts instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(attempts.toString());
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }


}