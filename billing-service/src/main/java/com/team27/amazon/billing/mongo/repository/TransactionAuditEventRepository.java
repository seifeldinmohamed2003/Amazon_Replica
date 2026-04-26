package com.team27.amazon.billing.mongo.repository;

import com.team27.amazon.billing.mongo.model.TransactionAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionAuditEventRepository
        extends MongoRepository<TransactionAuditEvent, String> {

    List<TransactionAuditEvent> findByTransactionIdOrderByTimestampAsc(Long transactionId);
}
