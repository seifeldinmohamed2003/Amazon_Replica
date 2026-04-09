package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

        existing.setOrderId(updatedShipment.getOrderId());
        existing.setCarrier(updatedShipment.getCarrier());
        existing.setTrackingNumber(updatedShipment.getTrackingNumber());
        existing.setStatus(updatedShipment.getStatus());
        existing.setLatitude(updatedShipment.getLatitude());
        existing.setLongitude(updatedShipment.getLongitude());
        existing.setEstimatedDelivery(updatedShipment.getEstimatedDelivery());
        existing.setActualDelivery(updatedShipment.getActualDelivery());
        existing.setMetadata(updatedShipment.getMetadata());

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
            return new CarrierSummaryDTO(carrier, 0, 0, 0.0, 0.0);
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

        return new CarrierSummaryDTO(
                carrier,
                totalShipments,
                deliveredCount,
                averageDeliveryDays,
                onTimeRate
        );
    }

    public List<DelayedShipmentDTO> getDelayedShipments(Integer maxDeliveryAttempts) {
        if (maxDeliveryAttempts != null && maxDeliveryAttempts < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "maxDeliveryAttempts must be non-negative"
            );
        }

        List<Object[]> rows = shipmentRepository.findDelayedShipments(maxDeliveryAttempts);

        return rows.stream().map(row -> new DelayedShipmentDTO(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                ((java.sql.Date) row[4]).toLocalDate(),
                ((Number) row[5]).longValue(),
                ((Number) row[6]).intValue()
        )).toList();
    }
}