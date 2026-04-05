package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.dto.CarrierSummaryDTO;
import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.model.ShipmentStatus;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public CarrierSummaryDTO getCarrierSummary(String carrier,
                                               LocalDateTime start,
                                               LocalDateTime end) {

        List<Shipment> shipments =
                shipmentRepository.findByCarrierAndDateRange(carrier, start, end);

        if (shipments.isEmpty()) {
            throw new RuntimeException("Carrier not found or no data");
        }

        long totalShipments = shipments.size();

        long deliveredCount = shipments.stream()
                .filter(s -> s.getStatus() == ShipmentStatus.DELIVERED)
                .count();

        long onTimeCount = shipments.stream()
                .filter(s -> s.getActualDelivery() != null
                        && s.getEstimatedDelivery() != null
                        && !s.getActualDelivery().isAfter(s.getEstimatedDelivery()))
                .count();

        double onTimeRate = (totalShipments == 0)
                ? 0.0
                : (onTimeCount * 100.0 / totalShipments);

        double averageDeliveryDays = shipments.stream()
                .filter(s -> s.getActualDelivery() != null)
                .mapToLong(s ->
                        Duration.between(s.getCreatedAt(), s.getActualDelivery()).toDays()
                )
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
}