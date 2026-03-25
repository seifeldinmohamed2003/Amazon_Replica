package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.TransactionVoucher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionVoucherRepository extends JpaRepository<TransactionVoucher, Long> {
}