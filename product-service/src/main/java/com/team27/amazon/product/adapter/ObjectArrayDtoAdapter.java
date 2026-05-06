package com.team27.amazon.product.adapter;

import org.springframework.stereotype.Component;

import com.team27.amazon.product.dto.ProductSalesDTO;
import com.team27.amazon.product.dto.TopProductDTO;

@Component
public class ObjectArrayDtoAdapter {

public ProductSalesDTO toProductSalesDTO(Long productId, String productName, Object[] result) {
    Long totalUnitsSold = 0L;
    Double totalRevenue = 0.0;

    if (result != null) {
        Object[] row = result;

        if (result.length == 1 && result[0] instanceof Object[] nested) {
            row = nested;
        }

        if (row.length >= 2) {
            totalUnitsSold = row[0] == null ? 0L : ((Number) row[0]).longValue();
            totalRevenue = row[1] == null ? 0.0 : ((Number) row[1]).doubleValue();
        }
    }

    Double averageSellingPrice = totalUnitsSold == 0 ? 0.0 : totalRevenue / totalUnitsSold;

    return ProductSalesDTO.builder()
            .productId(productId)
            .name(productName)
            .totalUnitsSold(totalUnitsSold)
            .totalRevenue(totalRevenue)
            .averageSellingPrice(averageSellingPrice)
            .build();
}

    public TopProductDTO toTopProductDTO(Object[] row) {
        return TopProductDTO.builder()
                .productId(((Number) row[0]).longValue())
                .name((String) row[1])
                .rating(row[2] == null ? 0.0 : ((Number) row[2]).doubleValue())
                .totalSales(row[3] == null ? 0L : ((Number) row[3]).longValue())
                .build();
    }
}