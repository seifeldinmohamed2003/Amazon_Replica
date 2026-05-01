package com.team27.amazon.billing.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team27.amazon.billing.dto.RevenueReportDTO;
import com.team27.amazon.billing.dto.TransactionDetailsDTO;
import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import com.team27.amazon.billing.dto.VoucherUsageDTO;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.service.BillingService;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private BillingService billingService;


    @GetMapping("/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = (startDate != null)
                ? LocalDate.parse(startDate).atStartOfDay()
                : LocalDateTime.of(2000, 1, 1, 0, 0);

        LocalDateTime end = (endDate != null)
                ? LocalDate.parse(endDate).atTime(23, 59, 59)
                : LocalDateTime.now();

        return ResponseEntity.ok(billingService.searchTransactions(status, start, end));
    }

    @PutMapping("/{id}/refund")
    public ResponseEntity<Transaction> refundTransaction(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(billingService.processRefund(id, body.get("reason")));
    }

    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserTransactionSummaryDTO> getUserTransactionSummary(
            @PathVariable Long userId) {
        return ResponseEntity.ok(billingService.getUserTransactionSummary(userId));
    }

@PostMapping("/order/{orderId}")
public ResponseEntity<Transaction> processTransactionForOrder(
        @PathVariable Long orderId,
        @RequestBody Map<String, String> body,
        @RequestParam(defaultValue = "false") boolean simulateFailure) {

    Transaction t = billingService.processTransactionForOrder(
            orderId,
            body.get("method"),
            body.get("cardLastFour"),
            simulateFailure
    );

    return ResponseEntity.status(201).body(t);
}
    @PostMapping("/{transactionId}/voucher/{voucherId}")
    public ResponseEntity<TransactionDetailsDTO> applyVoucher(
            @PathVariable Long transactionId,
            @PathVariable Long voucherId) {
        billingService.applyVoucherToTransaction(transactionId, voucherId);
        return ResponseEntity.ok(billingService.getTransactionDetails(transactionId));
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.status(201).body(billingService.saveTransaction(transaction));
    }
    // ── S5-F6 ── GET /api/transactions/reports/revenue?startDate=&endDate= ──

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        return ResponseEntity.ok(billingService.getRevenueReport(start, end));
    }

    // ── S5-F7 ── PUT /api/transactions/{id}/retry ────────────────────────────

    @PutMapping("/{id}/retry")
    public ResponseEntity<Transaction> retryTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.retryTransaction(id));
    }

    // ── S5-F8 ── GET /api/transactions/{transactionId}/details ───────────────

    @GetMapping("/{transactionId}/details")
    public ResponseEntity<TransactionDetailsDTO> getTransactionDetails(
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(billingService.getTransactionDetails(transactionId));
    }

    // ── S5-F9 ── GET /api/transactions/voucher/top-used?limit={n} ────────────

    @GetMapping("/voucher/top-used")
    public ResponseEntity<List<VoucherUsageDTO>> getTopUsedVouchers(
            @RequestParam int limit) {
        return ResponseEntity.ok(billingService.getTopUsedVouchers(limit));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(billingService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getTransactionById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> updateTransaction(
            @PathVariable Long id,
            @RequestBody Transaction transaction) {
        return ResponseEntity.ok(billingService.updateTransaction(id, transaction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        billingService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

}