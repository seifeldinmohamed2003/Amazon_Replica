package com.team27.amazon.shipping.service;

import com.team27.amazon.shipping.model.Shipment;
import com.team27.amazon.shipping.repository.ShipmentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
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
}