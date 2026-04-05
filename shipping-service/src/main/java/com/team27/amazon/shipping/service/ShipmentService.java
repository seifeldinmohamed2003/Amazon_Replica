package com.team27.amazon.shipping.service;

public class ShipmentService {
    import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

    @Service
    public class ShipmentService {

        private final ShipmentRepository shipmentRepository;

        public ShipmentService(ShipmentRepository shipmentRepository) {
            this.shipmentRepository = shipmentRepository;
        }

        @Transactional
        public int purgeOldShipments(int olderThanDays) {

            LocalDateTime cutoff = LocalDateTime.now()
                    .minusDays(olderThanDays);

            return shipmentRepository.deleteOlderThan(cutoff);
        }
    }
}
