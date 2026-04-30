package com.team27.amazon.billing.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.team27.amazon.billing.model.AuditLogDocument;

import java.util.List;

@Repository
public interface TransactionAuditRepository extends MongoRepository<AuditLogDocument, String> {
    
    // Finds all logs for a transaction, sorted by newest first
    @Query(value = "{ 'transactionId' : ?0 }", sort = "{ 'timestamp' : 1 }")
    List<AuditLogDocument> findAllByTransactionId(String transactionId);
}