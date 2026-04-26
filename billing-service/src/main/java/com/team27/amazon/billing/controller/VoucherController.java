package com.team27.amazon.billing.controller;

import com.team27.amazon.billing.model.Voucher;
import com.team27.amazon.billing.repository.VoucherRepository;
import com.team27.amazon.billing.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private static final String SVC = "billing-service";

    @Autowired private VoucherRepository voucherRepository;
    @Autowired private BillingService billingService;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    // ── cache helpers ──────────────────────────────────────────────────────
    private void invalidate(String key) {
        try { redisTemplate.delete(key); } catch (Exception ignored) {}
    }
    private void invalidatePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception ignored) {}
    }

    // ── CRUD ───────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Voucher> createVoucher(@RequestBody Voucher voucher) {
        Voucher saved = voucherRepository.save(voucher);
        // no existing cache to invalidate on create
        billingService.notifyObservers("VOUCHER_CREATED", java.util.Map.of(
                "action", "VOUCHER_CREATED",
                "voucherId", saved.getId(),
                "transactionId", -1L,
                "timestamp", java.time.LocalDateTime.now()
        ));
        return ResponseEntity.status(201).body(saved);
    }

    @GetMapping
    public ResponseEntity<List<Voucher>> getAllVouchers() {
        return ResponseEntity.ok(voucherRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Voucher> getVoucherById(@PathVariable Long id) {
        String key = SVC + "::voucher::" + id;
        try {
            Voucher cached = (Voucher) redisTemplate.opsForValue().get(key);
            if (cached != null) return ResponseEntity.ok(cached);
        } catch (Exception ignored) {}

        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found"));
        try { redisTemplate.opsForValue().set(key, v, 15, TimeUnit.MINUTES); } catch (Exception ignored) {}
        return ResponseEntity.ok(v);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Voucher> updateVoucher(@PathVariable Long id, @RequestBody Voucher voucher) {
        voucher.setId(id);
        Voucher saved = voucherRepository.save(voucher);
        invalidate(SVC + "::voucher::" + id);
        invalidatePattern(SVC + "::S5-F9::*");
        billingService.notifyObservers("VOUCHER_UPDATED", java.util.Map.of(
                "action", "VOUCHER_UPDATED",
                "voucherId", id,
                "transactionId", -1L,
                "timestamp", java.time.LocalDateTime.now()
        ));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherRepository.deleteById(id);
        invalidate(SVC + "::voucher::" + id);
        invalidatePattern(SVC + "::S5-F9::*");
        return ResponseEntity.noContent().build();
    }
}
