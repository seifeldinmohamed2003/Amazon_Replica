package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.dto.*;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.security.jwt.JwtService;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired private BillingService billingService;
    @Autowired private JwtService jwtService;

    // ── helpers ────────────────────────────────────────────────────────────

    private Long extractUid(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return jwtService.extractUserId(authHeader.substring(7));
    }

    private String extractRole(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return jwtService.extractRole(authHeader.substring(7));
    }

    // ── M1 CRUD ────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@RequestBody Transaction transaction) {
        return ResponseEntity.status(201).body(billingService.saveTransaction(transaction));
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
    public ResponseEntity<Transaction> updateTransaction(@PathVariable Long id,
                                                         @RequestBody Transaction transaction) {
        return ResponseEntity.ok(billingService.updateTransaction(id, transaction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        billingService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    // ── M1 Feature endpoints ───────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<Transaction>> searchTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        java.time.LocalDateTime start = (startDate != null)
                ? LocalDate.parse(startDate).atStartOfDay()
                : java.time.LocalDateTime.of(2000, 1, 1, 0, 0);
        java.time.LocalDateTime end = (endDate != null)
                ? LocalDate.parse(endDate).atTime(23, 59, 59)
                : java.time.LocalDateTime.now();

        return ResponseEntity.ok(billingService.searchTransactions(status, start, end));
    }

    // S5-F2
    @PutMapping("/{id}/refund")
    public ResponseEntity<Transaction> refundTransaction(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(billingService.processRefund(id, body.get("reason")));
    }

    // S5-F3
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<UserTransactionSummaryDTO> getUserTransactionSummary(
            @PathVariable Long userId) {
        return ResponseEntity.ok(billingService.getUserTransactionSummary(userId));
    }

    // S5-F4 — now with optional ?simulateFailure=true
    @PostMapping("/order/{orderId}")
    public ResponseEntity<Transaction> processTransactionForOrder(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "false") boolean simulateFailure,
            @RequestBody Map<String, String> body) {
        Transaction t = billingService.processTransactionForOrder(
                orderId,
                body.get("method"),
                body.get("cardLastFour"),
                simulateFailure
        );
        return ResponseEntity.status(201).body(t);
    }

    // S5-F5
    @PostMapping("/{transactionId}/voucher/{voucherId}")
    public ResponseEntity<TransactionDetailsDTO> applyVoucher(
            @PathVariable Long transactionId,
            @PathVariable Long voucherId) {
        billingService.applyVoucherToTransaction(transactionId, voucherId);
        return ResponseEntity.ok(billingService.getTransactionDetails(transactionId));
    }

    // S5-F6
    @GetMapping("/reports/revenue")
    public ResponseEntity<RevenueReportDTO> getRevenueReport(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        java.time.LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        java.time.LocalDateTime end   = LocalDate.parse(endDate).atTime(23, 59, 59);
        return ResponseEntity.ok(billingService.getRevenueReport(start, end));
    }

    // S5-F7
    @PutMapping("/{id}/retry")
    public ResponseEntity<Transaction> retryTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.retryTransaction(id));
    }

    // S5-F8
    @GetMapping("/{transactionId}/details")
    public ResponseEntity<TransactionDetailsDTO> getTransactionDetails(
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(billingService.getTransactionDetails(transactionId));
    }

    // S5-F9
    @GetMapping("/voucher/top-used")
    public ResponseEntity<List<VoucherUsageDTO>> getTopUsedVouchers(@RequestParam int limit) {
        return ResponseEntity.ok(billingService.getTopUsedVouchers(limit));
    }

    // ── M2 FEATURES ────────────────────────────────────────────────────────

    // S5-F10
    @GetMapping("/analytics/category")
    public ResponseEntity<List<CategoryRevenueDTO>> getCategoryRevenue(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return ResponseEntity.ok(billingService.getCategoryRevenue(
                LocalDate.parse(startDate), LocalDate.parse(endDate)));
    }

    // S5-F11
    @GetMapping("/{transactionId}/lifecycle")
    public ResponseEntity<List<LifecycleEventDTO>> getTransactionLifecycle(
            @PathVariable Long transactionId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long uid  = extractUid(authHeader);
        String role = extractRole(authHeader);
        return ResponseEntity.ok(billingService.getTransactionLifecycle(transactionId, uid, role));
    }

    // S5-F12
    @PostMapping("/{id}/refund-items")
    public ResponseEntity<Transaction> processPartialRefund(
            @PathVariable Long id,
            @RequestBody RefundRequest request) {
        return ResponseEntity.ok(billingService.processPartialRefund(id, request));
    }
}
