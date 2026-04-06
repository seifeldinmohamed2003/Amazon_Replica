package com.team27.amazon.billing.service;

import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class BillingService {

    @Autowired
    private TransactionRepository transactionRepository;


    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.searchTransactions(status, startDate, endDate);
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }
    @Transactional
    public Transaction processRefund(Long id, String reason) {

        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already refunded");
        }

        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMPLETED transactions allowed");
        }

        transaction.setStatus(TransactionStatus.REFUNDED);

        Map<String, Object> details = transaction.getTransactionDetails();

        if (details == null) {
            details = new HashMap<>();
        }

        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());

        transaction.setTransactionDetails(details);
        return transactionRepository.save(transaction);
    }
}