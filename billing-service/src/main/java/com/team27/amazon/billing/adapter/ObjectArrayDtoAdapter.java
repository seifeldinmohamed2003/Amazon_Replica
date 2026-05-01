package com.team27.amazon.billing.adapter;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.team27.amazon.billing.dto.VoucherUsageDTO;
import com.team27.amazon.billing.model.Voucher;

@Component
public class ObjectArrayDtoAdapter {

    public String toTransactionMethod(Object[] row) {
        return (String) row[0];
    }

    public long toTransactionCount(Object[] row) {
        return ((Number) row[1]).longValue();
    }

    public double toTransactionSum(Object[] row) {
        return ((Number) row[2]).doubleValue();
    }

    public VoucherUsageDTO toVoucherUsageDTO(Object[] row) {
        Voucher voucher = (Voucher) row[0];

        boolean expired = voucher.getExpiryDate() != null
                && voucher.getExpiryDate().isBefore(LocalDateTime.now());

        return VoucherUsageDTO.builder()
                .voucherId(voucher.getId())
                .code(voucher.getCode())
.discountType(voucher.getDiscountType().name())        
        .discountValue(voucher.getDiscountValue())
                .timesUsed(((Number) row[1]).intValue())
                .totalDiscountGiven(((Number) row[2]).doubleValue())
                .active(voucher.getActive())
                .expired(expired)
                .build();
    }
}