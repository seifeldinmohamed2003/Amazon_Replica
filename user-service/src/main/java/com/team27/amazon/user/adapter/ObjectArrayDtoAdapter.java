package com.team27.amazon.user.adapter;

import org.springframework.stereotype.Component;

import com.team27.amazon.user.dto.TopBuyerDTO;
import com.team27.amazon.user.dto.UserOrderSummaryDTO;

@Component
public class ObjectArrayDtoAdapter {

    public UserOrderSummaryDTO adapt(Object[] row) {
        Object[] actualRow = unwrap(row);

        return UserOrderSummaryDTO.builder()
                .userId(toLong(actualRow[0]))
                .name((String) actualRow[1])
                .totalOrders(toLong(actualRow[2]))
                .completedOrders(toLong(actualRow[3]))
                .cancelledOrders(toLong(actualRow[4]))
                .totalSpent(toDouble(actualRow[5]))
                .averageOrderValue(toDouble(actualRow[6]))
                .build();
    }

    public TopBuyerDTO toTopBuyerDTO(Object[] row) {
        Object[] actualRow = unwrap(row);

        return TopBuyerDTO.builder()
                .userId(toLong(actualRow[0]))
                .name((String) actualRow[1])
                .totalSpent(toDouble(actualRow[2]))
                .orderCount(toLong(actualRow[3]))
                .build();
    }

    private Object[] unwrap(Object[] row) {
        if (row != null && row.length == 1 && row[0] instanceof Object[]) {
            return (Object[]) row[0];
        }
        return row;
    }

    private Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private Double toDouble(Object value) {
        return value == null ? 0.0 : ((Number) value).doubleValue();
    }
}