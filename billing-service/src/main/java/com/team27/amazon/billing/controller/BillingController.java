package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class BillingController {

    @Autowired
    private BillingService billingService;


    @GetMapping("/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @RequestParam(required = false) String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(
                billingService.searchTransactions(
                        status,
                        startDate.atStartOfDay(),
                        endDate.atTime(23, 59, 59)
                )
        );
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Transaction> refundTransaction(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String reason = body.get("reason");
        return ResponseEntity.ok(billingService.processRefund(id, reason));
    }

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserTransactionSummaryDTO> getUserTransactionSummary(@PathVariable Long userId) {
        return ResponseEntity.ok(billingService.getUserTransactionSummary(userId));
    }

    @PostMapping("/order/{orderId}")
    public ResponseEntity<Transaction> processTransactionForOrder(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body
    ) {
        Transaction t = billingService.processTransactionForOrder(
                orderId,
                body.get("method"),
                body.get("cardLastFour")
        );
        return ResponseEntity.status(201).body(t);
    }

    @PostMapping("/{transactionId}/voucher/{voucherId}")
    public ResponseEntity<Transaction> applyVoucher(
            @PathVariable Long transactionId,
            @PathVariable Long voucherId
    ) {
        return ResponseEntity.ok(billingService.applyVoucherToTransaction(transactionId, voucherId));
    }

//POST endpoint to make creating transactions easier
    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.ok(billingService.saveTransaction(transaction));
    }
}