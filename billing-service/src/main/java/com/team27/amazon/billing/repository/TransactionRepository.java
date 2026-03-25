package com.team27.amazon.billing.repository;

import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Add custom queries here as you implement features
}