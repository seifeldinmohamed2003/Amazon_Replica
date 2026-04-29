package com.team27.amazon.billing.repository;

import com.team27.amazon.common.events.TransactionAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TransactionAuditEventRepository extends MongoRepository<TransactionAuditEvent, String> {
}