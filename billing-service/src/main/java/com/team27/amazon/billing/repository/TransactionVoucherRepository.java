package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.TransactionVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionVoucherRepository extends JpaRepository<TransactionVoucher, Long> {

    @Query(value = """
        SELECT * FROM transaction_vouchers
        WHERE transaction_id = :transactionId AND voucher_id = :voucherId
        LIMIT 1
        """, nativeQuery = true)
    Optional<TransactionVoucher> findByTransactionIdAndVoucherId(
            @Param("transactionId") Long transactionId,
            @Param("voucherId") Long voucherId
    );
}