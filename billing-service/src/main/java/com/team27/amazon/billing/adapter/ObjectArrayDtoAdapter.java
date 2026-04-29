package com.team27.amazon.billing.adapter;

import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Converts native SQL Object[] rows from getTransactionSummaryByUser query
// into UserTransactionSummaryDTO — satisfies the Adapter pattern requirement
// for M1 features that use Object[] projections (S5-F3)
@Component
public class ObjectArrayDtoAdapter {

    public UserTransactionSummaryDTO adapt(Long userId, List<Object[]> rows) {
        Map<String, Double> breakdown = new HashMap<>();
        long totalTransactions = 0;
        double totalAmount = 0.0;

        for (Object[] row : rows) {
            String method = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double sum = ((Number) row[2]).doubleValue();
            breakdown.put(method, sum);
            totalTransactions += count;
            totalAmount += sum;
        }

        return UserTransactionSummaryDTO.builder()
                .userId(userId)
                .totalTransactions(totalTransactions)
                .totalAmount(totalAmount)
                .methodBreakdown(breakdown)
                .build();
    }
}