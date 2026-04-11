package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.model.TransactionVoucher;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/transaction-vouchers")
public class TransactionVoucherController {

    @Autowired
    private BillingService billingService;

    @PostMapping
    public ResponseEntity<TransactionVoucher> create(@RequestBody TransactionVoucher tv) {
        return ResponseEntity.ok(billingService.createTransactionVoucher(tv));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionVoucher> getById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getTransactionVoucherById(id));
    }

    @GetMapping
    public ResponseEntity<List<TransactionVoucher>> getAll() {
        return ResponseEntity.ok(billingService.getAllTransactionVouchers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        billingService.deleteTransactionVoucher(id);
        return ResponseEntity.noContent().build();
    }
}