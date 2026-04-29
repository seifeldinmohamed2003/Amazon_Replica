package com.team27.amazon.user.adapter;

import com.team27.amazon.user.dto.UserOrderSummaryDTO;
import org.springframework.stereotype.Component;

@Component
public class ObjectArrayDtoAdapter {

    public UserOrderSummaryDTO adapt(Object[] row) {
        return UserOrderSummaryDTO.builder()
                .userId(((Number) row[0]).longValue())
                .name((String) row[1])
                .totalOrders(((Number) row[2]).longValue())
                .completedOrders(((Number) row[3]).longValue())
                .cancelledOrders(((Number) row[4]).longValue())
                .totalSpent(((Number) row[5]).doubleValue())
                .averageOrderValue(((Number) row[6]).doubleValue())
                .build();
    }
}