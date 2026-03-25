package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
}