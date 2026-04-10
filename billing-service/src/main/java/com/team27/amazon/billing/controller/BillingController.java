package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.dto.RevenueReportDTO;
import com.team27.amazon.billing.dto.TransactionDetailsDTO;
import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import com.team27.amazon.billing.dto.VoucherUsageDTO;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class BillingController {

    @Autowired
    private BillingService billingService;

    // ── existing ─────────────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @RequestParam(required = false) String status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
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
            @RequestBody Map<String, String> body) {
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
            @PathVariable Long voucherId) {
        return ResponseEntity.ok(billingService.applyVoucherToTransaction(transactionId, voucherId));
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.ok(billingService.saveTransaction(transaction));
    }

    // ── S5-F6 ── GET /api/transactions/reports/revenue?startDate=&endDate= ──

    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(billingService.getRevenueReport(startDate, endDate));
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
}