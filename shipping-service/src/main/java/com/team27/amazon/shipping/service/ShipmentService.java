package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.dto.DelayedShipmentDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.http.HttpStatus;
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

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
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

        List<Shipment> shipments =
                shipmentRepository.findByCarrierAndDateRange(carrier, start, end);

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
                .filter(s -> s.getActualDelivery() != null)
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
                (LocalDate) row[4],
                ((Number) row[5]).longValue(),
                ((Number) row[6]).intValue()
        )).toList();
    }
}