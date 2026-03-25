package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.model.*;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final BillingService billingService;

    public TransactionController(BillingService billingService) {
        this.billingService = billingService;
    }

    // ── HEALTH ────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ── TRANSACTION CRUD ──────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Transaction> create(@RequestBody Transaction t) {
        return ResponseEntity.ok(billingService.createTransaction(t));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getTransactionById(id));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAll() {
        return ResponseEntity.ok(billingService.getAllTransactions());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> update(@PathVariable Long id, @RequestBody Transaction t) {
        return ResponseEntity.ok(billingService.updateTransaction(id, t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        billingService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    // ── VOUCHER CRUD ──────────────────────────────────────────────
    @PostMapping("/vouchers")
    public ResponseEntity<Voucher> createVoucher(@RequestBody Voucher v) {
        return ResponseEntity.ok(billingService.createVoucher(v));
    }

    @GetMapping("/vouchers/{id}")
    public ResponseEntity<Voucher> getVoucherById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getVoucherById(id));
    }

    @GetMapping("/vouchers")
    public ResponseEntity<List<Voucher>> getAllVouchers() {
        return ResponseEntity.ok(billingService.getAllVouchers());
    }

    @PutMapping("/vouchers/{id}")
    public ResponseEntity<Voucher> updateVoucher(@PathVariable Long id, @RequestBody Voucher v) {
        return ResponseEntity.ok(billingService.updateVoucher(id, v));
    }

    @DeleteMapping("/vouchers/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        billingService.deleteVoucher(id);
        return ResponseEntity.noContent().build();
    }

    // ── TRANSACTION VOUCHER CRUD ──────────────────────────────────
    @PostMapping("/transaction-vouchers")
    public ResponseEntity<TransactionVoucher> createTV(@RequestBody TransactionVoucher tv) {
        return ResponseEntity.ok(billingService.createTransactionVoucher(tv));
    }

    @GetMapping("/transaction-vouchers/{id}")
    public ResponseEntity<TransactionVoucher> getTVById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getTransactionVoucherById(id));
    }

    @GetMapping("/transaction-vouchers")
    public ResponseEntity<List<TransactionVoucher>> getAllTVs() {
        return ResponseEntity.ok(billingService.getAllTransactionVouchers());
    }

    @DeleteMapping("/transaction-vouchers/{id}")
    public ResponseEntity<Void> deleteTV(@PathVariable Long id) {
        billingService.deleteTransactionVoucher(id);
        return ResponseEntity.noContent().build();
    }
}