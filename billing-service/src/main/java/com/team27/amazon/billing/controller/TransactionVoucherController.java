package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.model.TransactionVoucher;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/transaction-vouchers")
public class TransactionVoucherController {

    private static final String SVC = "billing-service";

    @Autowired private BillingService billingService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    @PostMapping
    public ResponseEntity<TransactionVoucher> create(@RequestBody TransactionVoucher tv) {
        return ResponseEntity.status(201).body(billingService.createTransactionVoucher(tv));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionVoucher> getById(@PathVariable Long id) {
        String key = SVC + "::transaction-voucher::" + id;
        try {
            TransactionVoucher cached = (TransactionVoucher) redisTemplate.opsForValue().get(key);
            if (cached != null) return ResponseEntity.ok(cached);
        } catch (Exception ignored) {}

        TransactionVoucher tv = billingService.getTransactionVoucherById(id);
        try { redisTemplate.opsForValue().set(key, tv, 15, TimeUnit.MINUTES); } catch (Exception ignored) {}
        return ResponseEntity.ok(tv);
    }

    @GetMapping
    public ResponseEntity<List<TransactionVoucher>> getAll() {
        return ResponseEntity.ok(billingService.getAllTransactionVouchers());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        billingService.deleteTransactionVoucher(id);
        try { redisTemplate.delete(SVC + "::transaction-voucher::" + id); } catch (Exception ignored) {}
        return ResponseEntity.noContent().build();
    }
}
