package com.team27.amazon.shipping.adapter;

import java.sql.Date;

import org.springframework.stereotype.Component;

import com.team27.amazon.shipping.dto.DelayedShipmentDTO;

@Component
public class ObjectArrayDtoAdapter {

    public DelayedShipmentDTO toDelayedShipmentDTO(Object[] row) {
        return DelayedShipmentDTO.builder()
                .shipmentId(((Number) row[0]).longValue())
                .orderId(((Number) row[1]).longValue())
                .carrier((String) row[2])
                .trackingNumber((String) row[3])
                .estimatedDelivery(((Date) row[4]).toLocalDate())
                .daysOverdue(((Number) row[5]).longValue())
                .deliveryAttempts(((Number) row[6]).intValue())
                .build();
    }
}