package com.team27.amazon.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team27.amazon.billing.dto.*;
import com.team27.amazon.billing.factory.EventType;
import com.team27.amazon.billing.model.*;
import com.team27.amazon.billing.observer.EntityObserver;
import com.team27.amazon.billing.observer.MongoEventLogger;
import com.team27.amazon.billing.mongo.repository.TransactionAuditEventRepository;
import com.team27.amazon.billing.mongo.repository.OrderEventRepository;
import com.team27.amazon.billing.mongo.repository.ShipmentEventRepository;
import com.team27.amazon.billing.mongo.model.TransactionAuditEvent;
import com.team27.amazon.billing.mongo.model.OrderEvent;
import com.team27.amazon.billing.mongo.model.ShipmentEvent;
import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.billing.repository.TransactionVoucherRepository;
import com.team27.amazon.billing.repository.VoucherRepository;
import com.team27.amazon.billing.strategy.*;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class BillingService {

    @Autowired
    private CacheService cacheService;
    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    // ── repositories ──────────────────────────────────────────────────────
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private VoucherRepository voucherRepository;
    @Autowired private TransactionVoucherRepository transactionVoucherRepository;

    // ── mongo ─────────────────────────────────────────────────────────────
    @Autowired private TransactionAuditEventRepository auditRepo;
    @Autowired private OrderEventRepository orderEventRepo;
    @Autowired private ShipmentEventRepository shipmentEventRepo;

    // ── redis ─────────────────────────────────────────────────────────────
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    // ── observer list ─────────────────────────────────────────────────────
    private final List<EntityObserver> observers = new ArrayList<>();

    @PostConstruct
    public void registerObservers() {
        MongoEventLogger logger = new MongoEventLogger(auditRepo, EventType.TRANSACTION_AUDIT);
        register(logger);
    }


    public void register(EntityObserver observer)   { observers.add(observer); }
    public void unregister(EntityObserver observer) { observers.remove(observer); }

    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver o : observers) {
            o.onEvent(eventType, payload);
        }
    }

    // ── cache helpers ──────────────────────────────────────────────────────
    private static final String SVC = "billing-service";

    private void cacheSet(String key, Object value, long ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(key, value, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis set failed for key {}: {}", key, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cacheGet(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis get failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void invalidate(String key) {
        try { redisTemplate.delete(key); } catch (Exception e) {
            log.warn("Redis delete failed for key {}: {}", key, e.getMessage());
        }
    }

    private void invalidatePattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("Redis wildcard delete failed for pattern {}: {}", pattern, e.getMessage());
        }
    }

    private Map<String, Object> txPayload(Transaction t, String action) {
        Map<String, Object> p = new HashMap<>();
        p.put("action", action);
        p.put("transactionId", t.getId());
        p.put("method", t.getMethod() != null ? t.getMethod().name() : null);
        p.put("amount", t.getAmount());
        p.put("timestamp", LocalDateTime.now());
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M1 CRUD — save / update / delete with observer + cache invalidation
    // ═══════════════════════════════════════════════════════════════════════

    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        String key = "billing-service::S5-F1::" + status + "::" + startDate + "::" + endDate;
        Object cached = cacheService.get(key);
        if (cached != null) return (List<Transaction>) cached;
        List<Transaction> result = transactionRepository.searchTransactions(status, startDate, endDate);
        cacheService.set(key, result, 5);
        return result;
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction getTransactionById(Long id) {
        String key = SVC + "::transaction::" + id;
        Transaction cached = cacheGet(key);
        if (cached != null) return cached;

        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        cacheSet(key, t, 15);
        return t;
    }

    public Voucher getVoucherById(Long id) {
        String key = SVC + "::voucher::" + id;
        Voucher cached = cacheGet(key);
        if (cached != null) return cached;

        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voucher not found"));
        cacheSet(key, v, 15);
        return v;
    }

    public Transaction updateTransaction(Long id, Transaction updated) {
        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        if (updated.getAmount() != null)             t.setAmount(updated.getAmount());
        if (updated.getMethod() != null)             t.setMethod(updated.getMethod());
        if (updated.getStatus() != null)             t.setStatus(updated.getStatus());
        if (updated.getTransactionDetails() != null) t.setTransactionDetails(updated.getTransactionDetails());
        if (updated.getOrderId() != null)            t.setOrderId(updated.getOrderId());
        if (updated.getUserId() != null)             t.setUserId(updated.getUserId());
        Transaction saved = transactionRepository.save(t);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", t.getId());
        notifyObservers("TRANSACTION_UPDATED", payload);

        invalidate(SVC + "::transaction::" + id);
        invalidatePattern(SVC + "::S5-F8::*");
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");
        notifyObservers("TRANSACTION_UPDATED", txPayload(saved, "TRANSACTION_UPDATED"));
        return saved;
    }

    public void deleteTransaction(Long id) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", id);
        notifyObservers("TRANSACTION_DELETED", payload);

        transactionRepository.deleteById(id);
        invalidate(SVC + "::transaction::" + id);
        invalidatePattern(SVC + "::S5-F8::*");
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");
    }

    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getCreatedAt() == null) transaction.setCreatedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transaction.getId());
        notifyObservers("TRANSACTION_CREATED", payload);

        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");
        notifyObservers("TRANSACTION_CREATED", txPayload(saved, "TRANSACTION_CREATED"));
        return saved;
    }

    // ── Voucher CRUD ───────────────────────────────────────────────────────
    public TransactionVoucher createTransactionVoucher(TransactionVoucher tv) {
        if (tv.getAppliedAt() == null) tv.setAppliedAt(LocalDateTime.now());
        return transactionVoucherRepository.save(tv);
    }

    public TransactionVoucher getTransactionVoucherById(Long id) {
        return transactionVoucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TransactionVoucher not found"));
    }

    public List<TransactionVoucher> getAllTransactionVouchers() {
        return transactionVoucherRepository.findAll();
    }

    public void deleteTransactionVoucher(Long id) {
        transactionVoucherRepository.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M1 FEATURES
    // ═══════════════════════════════════════════════════════════════════════

    // ── S5-F2 ── Process Refund ────────────────────────────────────────────
    @Transactional
    public Transaction processRefund(Long id, String reason) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.REFUNDED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already refunded");
        if (transaction.getStatus() != TransactionStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMPLETED transactions allowed");

        transaction.setStatus(TransactionStatus.REFUNDED);
        Map<String, Object> details = transaction.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        transaction.setTransactionDetails(details);
        Transaction saved = transactionRepository.save(transaction);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transaction.getId());
        payload.put("method", transaction.getMethod() != null ? transaction.getMethod().name() : null);
        payload.put("amount", transaction.getAmount());
        payload.put("refundReason", reason);
        payload.put("refundedAt", LocalDateTime.now().toString());
        notifyObservers("REFUNDED", payload);

        invalidate(SVC + "::transaction::" + id);
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");
        notifyObservers("REFUNDED", txPayload(saved, "REFUNDED"));
        return saved;



    }

    // ── S5-F3 ── User Transaction Summary ─────────────────────────────────
    public UserTransactionSummaryDTO getUserTransactionSummary(Long userId) {
        String key = SVC + "::S5-F3::" + userId;
        UserTransactionSummaryDTO cached = cacheGet(key);
        if (cached != null) return cached;

        int userExists = transactionRepository.countUserById(userId);
        if (userExists == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");

        List<Object[]> rows = transactionRepository.getTransactionSummaryByUser(userId);
        Map<String, Double> methodBreakdown = new HashMap<>();
        long totalTransactions = 0;
        double totalAmount = 0.0;
        for (Object[] row : rows) {
            String method = (String) row[0];
            long count    = ((Number) row[1]).longValue();
            double sum    = ((Number) row[2]).doubleValue();
            methodBreakdown.put(method, sum);
            totalTransactions += count;
            totalAmount += sum;
        }

        UserTransactionSummaryDTO dto = UserTransactionSummaryDTO.builder()
                .userId(userId)
                .totalTransactions(totalTransactions)
                .totalAmount(totalAmount)
                .methodBreakdown(methodBreakdown)
                .build();
        cacheSet(key, dto, 10);
        return dto;
    }

    // ── S5-F4 ── Process Transaction for Order ────────────────────────────
    @Transactional
    public Transaction processTransactionForOrder(Long orderId, String method,
                                                  String cardLastFour, boolean simulateFailure) {
        String orderStatus = transactionRepository.findOrderStatusById(orderId);
        if (orderStatus == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        if (!orderStatus.equals("DELIVERED"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be DELIVERED to process payment");

        int completedCount = transactionRepository.countCompletedTransactionsByOrderId(orderId);
        if (completedCount > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");

        Double amount = transactionRepository.findOrderTotalAmountById(orderId);
        Long   userId = transactionRepository.findUserIdByOrderId(orderId);

        Map<String, Object> details = new HashMap<>();
        Transaction transaction = new Transaction();
        transaction.setOrderId(orderId);
        transaction.setUserId(userId);
        transaction.setAmount(amount != null ? amount : 0.0);
        transaction.setMethod(TransactionMethod.valueOf(method));
        transaction.setCreatedAt(LocalDateTime.now());

        if (simulateFailure) {
            details.put("gatewayResponse", "declined");
            details.put("failureReason", "simulated gateway failure");
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setTransactionDetails(details);
            Transaction saved = transactionRepository.save(transaction);
            invalidatePattern(SVC + "::S5-F10::*");
            invalidatePattern(SVC + "::S5-F11::*");
            notifyObservers("FAILED", txPayload(saved, "FAILED"));
            return saved;
        }

        details.put("gatewayResponse", "approved");
        if (cardLastFour != null) details.put("cardLastFour", cardLastFour);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDetails(details);
        Transaction saved = transactionRepository.save(transaction);

        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");
        notifyObservers("CREATED",    txPayload(saved, "CREATED"));
        notifyObservers("COMPLETED",  txPayload(saved, "COMPLETED"));
        return saved;
    }

    // ── S5-F5 ── Apply Voucher to Transaction ─────────────────────────────
    @Transactional
    public Transaction applyVoucherToTransaction(Long transactionId, Long voucherId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.COMPLETED ||
            transaction.getStatus() == TransactionStatus.REFUNDED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply voucher to a completed/cancelled transaction");

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found"));

        if (!voucher.getActive())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher is not active");
        if (voucher.getExpiryDate().isBefore(LocalDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher has expired");
        if (voucher.getCurrentUses() >= voucher.getMaxUses())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher usage limit reached");

        Optional<TransactionVoucher> duplicate = transactionVoucherRepository
                .findByTransactionIdAndVoucherId(transactionId, voucherId);
        if (duplicate.isPresent())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "voucher already applied");

        double discount = (voucher.getDiscountType() == DiscountType.PERCENTAGE)
                ? transaction.getAmount() * voucher.getDiscountValue() / 100
                : voucher.getDiscountValue();
        if (discount > transaction.getAmount()) discount = transaction.getAmount();

        TransactionVoucher tv = new TransactionVoucher();
        tv.setTransaction(transaction);
        tv.setVoucher(voucher);
        tv.setDiscountApplied(discount);
        tv.setAppliedAt(LocalDateTime.now());
        transactionVoucherRepository.save(tv);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", transactionId);
        payload.put("method", transaction.getMethod() != null ? transaction.getMethod().name() : null);
        payload.put("amount", transaction.getAmount());
        payload.put("voucherId", voucherId);
        payload.put("discountApplied", discount);
        notifyObservers("VOUCHER_APPLIED", payload);

        voucher.setCurrentUses(voucher.getCurrentUses() + 1);
        voucherRepository.save(voucher);



        invalidate(SVC + "::transaction::" + transactionId);
        invalidate(SVC + "::voucher::" + voucherId);
        invalidatePattern(SVC + "::S5-F8::*");
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");

        Map<String, Object> createdPayload = txPayload(transaction, "VOUCHER_APPLIED");
        createdPayload.put("voucherId", voucherId);
        notifyObservers("VOUCHER_APPLIED", createdPayload);

        return transactionRepository.findById(transactionId).get();
    }

    // ── S5-F6 ── Revenue Report ────────────────────────────────────────────
    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        String key = SVC + "::S5-F6::" + startDate + "_" + endDate;
        RevenueReportDTO cached = cacheGet(key);
        if (cached != null) return cached;

        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");

        Double totalRevenue    = transactionRepository.sumCompletedRevenue(startDate, endDate);
        Long   totalTx         = transactionRepository.countCompleted(startDate, endDate);
        Double refundedAmount  = transactionRepository.sumRefundedAmount(startDate, endDate);
        Long   refundCount     = transactionRepository.countRefunded(startDate, endDate);

        if (totalRevenue == null)   totalRevenue = 0.0;
        if (totalTx == null)        totalTx = 0L;
        if (refundedAmount == null) refundedAmount = 0.0;
        if (refundCount == null)    refundCount = 0L;

        double average = totalTx > 0 ? totalRevenue / totalTx : 0.0;

        RevenueReportDTO dto = RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTx)
                .averageTransaction(average)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
        cacheSet(key, dto, 10);
        return dto;
    }

    // ── S5-F7 ── Retry Failed Transaction ─────────────────────────────────
    @Transactional
    public Transaction retryTransaction(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id));

        if (tx.getStatus() != TransactionStatus.FAILED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED transactions can be retried. Current status: " + tx.getStatus());

        tx.setStatus(TransactionStatus.COMPLETED);
        Map<String, Object> details = tx.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        int currentRetry = (details.get("retryAttempt") instanceof Number n) ? n.intValue() : 0;
        details.put("retryAttempt", currentRetry + 1);
        details.put("gatewayResponse", "approved");
        tx.setTransactionDetails(details);
        Transaction saved = transactionRepository.save(tx);

        Map<String, Object> payload = new HashMap<>();
        payload.put("transactionId", tx.getId());
        payload.put("method", tx.getMethod() != null ? tx.getMethod().name() : null);
        payload.put("amount", tx.getAmount());
        notifyObservers("RETRY_ATTEMPTED", payload);

        invalidate(SVC + "::transaction::" + id);
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");

        Map<String, Object> createdPayload = txPayload(saved, "RETRY_ATTEMPTED");
        notifyObservers("RETRY_ATTEMPTED", createdPayload);
        return saved;
    }

    // ── S5-F8 ── Transaction Details ───────────────────────────────────────
    public TransactionDetailsDTO getTransactionDetails(Long transactionId) {
        String key = SVC + "::S5-F8::" + transactionId;
        TransactionDetailsDTO cached = cacheGet(key);
        if (cached != null) return cached;

        Transaction tx = transactionRepository.findByIdWithVouchers(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + transactionId));

        List<TransactionDetailsDTO.AppliedVoucherDTO> appliedVouchers = new ArrayList<>();
        double totalDiscount = 0.0;
        for (TransactionVoucher tv : tx.getTransactionVouchers()) {
            Voucher v = tv.getVoucher();
            appliedVouchers.add(new TransactionDetailsDTO.AppliedVoucherDTO(
                    v.getCode(), v.getDiscountType().name(),
                    tv.getDiscountApplied(), tv.getAppliedAt()));
            totalDiscount += tv.getDiscountApplied();
        }

        TransactionDetailsDTO dto = TransactionDetailsDTO.builder()
                .transactionId(tx.getId())
                .orderId(tx.getOrderId())
                .userId(tx.getUserId())
                .originalAmount(tx.getAmount())
                .method(tx.getMethod().name())
                .status(tx.getStatus().name())
                .transactionDetails(tx.getTransactionDetails())
                .appliedVouchers(appliedVouchers)
                .totalDiscount(totalDiscount)
                .finalAmount(tx.getAmount() - totalDiscount)
                .build();
        cacheSet(key, dto, 15);
        return dto;
    }

    // ── S5-F9 ── Top Used Vouchers ─────────────────────────────────────────
    public List<VoucherUsageDTO> getTopUsedVouchers(int limit) {
        String key = SVC + "::S5-F9::" + limit;
        List<VoucherUsageDTO> cached = cacheGet(key);
        if (cached != null) return cached;

        List<Object[]> rows = transactionVoucherRepository.findTopUsedVouchers();
        List<VoucherUsageDTO> result = new ArrayList<>();
        int count = 0;
        for (Object[] row : rows) {
            if (count >= limit) break;
            Voucher v             = (Voucher) row[0];
            Integer timesUsed     = ((Number) row[1]).intValue();
            Double  totalDiscount = ((Number) row[2]).doubleValue();
            boolean expired = v.getExpiryDate() != null &&
                    v.getExpiryDate().isBefore(LocalDateTime.now());
            result.add(VoucherUsageDTO.builder()
                    .voucherId(v.getId())
                    .code(v.getCode())
                    .discountType(v.getDiscountType().name())
                    .discountValue(v.getDiscountValue())
                    .timesUsed(timesUsed)
                    .totalDiscountGiven(totalDiscount)
                    .active(v.getActive())
                    .expired(expired)
                    .build());
            count++;
        }
        cacheSet(key, result, 10);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // M2 FEATURES
    // ═══════════════════════════════════════════════════════════════════════

    // ── S5-F10 ── Category Revenue with Return Impact ──────────────────────
    public List<CategoryRevenueDTO> getCategoryRevenue(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");

        // Log ANALYTICS_VIEWED before cache check (always runs)
        Map<String, Object> viewPayload = new HashMap<>();
        viewPayload.put("action", "ANALYTICS_VIEWED");
        viewPayload.put("transactionId", -1L);
        viewPayload.put("timestamp", LocalDateTime.now());
        notifyObservers("ANALYTICS_VIEWED", viewPayload);

        String key = SVC + "::S5-F10::" + startDate + "_" + endDate;
        List<CategoryRevenueDTO> cached = cacheGet(key);
        if (cached != null) return cached;

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = endDate.atTime(23, 59, 59, 999_000_000);

        // gross revenue + counts per category
        List<Object[]> rows = transactionRepository.getCategoryRevenue(start, end);
        Map<String, double[]> categoryMap = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String cat        = (String) row[0];
            double gross      = ((Number) row[1]).doubleValue();
            long   txCount    = ((Number) row[2]).longValue();
            long   refCount   = ((Number) row[3]).longValue();
            categoryMap.put(cat, new double[]{gross, 0.0, txCount, refCount});
        }

        // refunded revenue — use refundedItems if present, else full amount
        List<Object[]> refundRows = transactionRepository.getRefundedTransactionDetails(start, end);
        ObjectMapper om = new ObjectMapper();
        for (Object[] row : refundRows) {
            String category = (String) row[3];
            if (!categoryMap.containsKey(category)) continue;
            Object detailsObj = row[1];
            double refundedAmount = 0.0;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (detailsObj instanceof Map<?,?> m)
                        ? (Map<String, Object>) m
                        : om.readValue(detailsObj.toString(), Map.class);
                if (details != null && details.containsKey("refundedItems")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) details.get("refundedItems");
                    for (Map<String, Object> item : items) {
                        refundedAmount += ((Number) item.get("amount")).doubleValue();
                    }
                } else {
                    // fallback: full amount
                    refundedAmount = ((Number) row[2]).doubleValue();
                }
            } catch (Exception e) {
                refundedAmount = ((Number) row[2]).doubleValue();
            }
            categoryMap.get(category)[1] += refundedAmount;
        }

        List<CategoryRevenueDTO> result = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : categoryMap.entrySet()) {
            double[] vals = entry.getValue();
            double gross   = vals[0];
            double refunded = vals[1];
            long txCount   = (long) vals[2];
            long refCount  = (long) vals[3];
            double returnRate = txCount > 0 ? (double) refCount / txCount : 0.0;
            result.add(CategoryRevenueDTO.builder()
                    .category(entry.getKey())
                    .grossRevenue(gross)
                    .refundedRevenue(refunded)
                    .netRevenue(gross - refunded)
                    .transactionCount(txCount)
                    .refundCount(refCount)
                    .returnRate(returnRate)
                    .build());
        }

        cacheSet(key, result, 10);
        return result;
    }

    // ── S5-F11 ── Transaction Lifecycle Audit ─────────────────────────────
    public List<LifecycleEventDTO> getTransactionLifecycle(Long transactionId, Long callerUid, String callerRole) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        // ownership check
        boolean isAdmin = "ADMIN".equals(callerRole);
        if (!isAdmin && !tx.getUserId().equals(callerUid))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: not the transaction owner");

        String key = SVC + "::S5-F11::" + transactionId;
        List<LifecycleEventDTO> cached = cacheGet(key);
        if (cached != null) return cached;

        Long orderId = tx.getOrderId();
        List<Long> shipmentIds = transactionRepository.findShipmentIdsByOrderId(orderId);

        List<LifecycleEventDTO> timeline = new ArrayList<>();

        // TRANSACTION events
        for (TransactionAuditEvent e : auditRepo.findByTransactionIdOrderByTimestampAsc(transactionId)) {
            timeline.add(new LifecycleEventDTO(e.getTimestamp(), "TRANSACTION", e.getAction(), e.getDetails()));
        }

        // ORDER events
        for (OrderEvent e : orderEventRepo.findByOrderIdOrderByTimestampAsc(orderId)) {
            timeline.add(new LifecycleEventDTO(e.getTimestamp(), "ORDER", e.getAction(), e.getDetails()));
        }

        // SHIPMENT events
        if (!shipmentIds.isEmpty()) {
            for (ShipmentEvent e : shipmentEventRepo.findByShipmentIdInOrderByTimestampAsc(shipmentIds)) {
                timeline.add(new LifecycleEventDTO(e.getTimestamp(), "SHIPMENT", e.getAction(), e.getDetails()));
            }
        }

        // sort chronologically
        timeline.sort(Comparator.comparing(LifecycleEventDTO::getTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));

        cacheSet(key, timeline, 10);
        return timeline;
    }

    // ── S5-F12 ── Process Partial Item Refund ─────────────────────────────
    @Transactional
    public Transaction processPartialRefund(Long id, RefundRequest request) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (tx.getStatus() != TransactionStatus.COMPLETED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction must be COMPLETED to refund");

        // fetch order items
        List<Object[]> itemRows = transactionRepository.getOrderItemsByTransactionId(id);
        List<Long> allItemIds = new ArrayList<>();
        Map<Long, Double> itemAmounts = new LinkedHashMap<>();
        Map<Long, Integer> itemQty = new LinkedHashMap<>();
        for (Object[] row : itemRows) {
            Long   itemId = ((Number) row[0]).longValue();
            Double price  = ((Number) row[1]).doubleValue();
            int    qty    = ((Number) row[2]).intValue();
            allItemIds.add(itemId);
            itemAmounts.put(itemId, price * qty);
            itemQty.put(itemId, qty);
        }

        // validate requested item IDs if partial
        if (!Boolean.TRUE.equals(request.getRefundAll())) {
            List<Long> requested = request.getOrderItemIds();
            if (requested == null || requested.isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderItemIds must not be empty");
            for (Long itemId : requested) {
                if (transactionRepository.countOrderItemBelongsToTransaction(id, itemId) == 0)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "OrderItem " + itemId + " does not belong to this transaction's order");
            }
        }

        // select strategy
        RefundStrategySelector selector = new RefundStrategySelector(allItemIds, itemAmounts);
        RefundStrategy strategy = selector.select(tx, request);
        RefundResult result = strategy.calculateRefund(tx, request);

        if ("NoRefundStrategy".equals(result.getStrategyName())) {
            // log REFUND_DENIED
            Map<String, Object> denialPayload = txPayload(tx, "REFUND_DENIED");
            denialPayload.put("refundStrategy", "NoRefundStrategy");
            denialPayload.put("denialReason", "return window expired");
            denialPayload.put("requestedOrderItemIds", request.getOrderItemIds());
            notifyObservers("REFUND_DENIED", denialPayload);
            invalidatePattern(SVC + "::S5-F10::*");
            invalidatePattern(SVC + "::S5-F11::*");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "return window expired");
        }

        // apply refund
        tx.setStatus(TransactionStatus.REFUNDED);
        Map<String, Object> details = tx.getTransactionDetails();
        if (details == null) details = new HashMap<>();

        // build refundedItems list
        List<Map<String, Object>> refundedItems = new ArrayList<>();
        for (Long itemId : result.getRefundedItemIds()) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderItemId", itemId);
            item.put("quantity", itemQty.getOrDefault(itemId, 1));
            item.put("amount", itemAmounts.getOrDefault(itemId, 0.0));
            refundedItems.add(item);
        }

        details.put("refundAmount",    result.getRefundAmount());
        details.put("refundedItems",   refundedItems);
        details.put("refundStrategy",  result.getStrategyName());
        details.put("refundReason",    request.getReason());
        details.put("refundedAt",      LocalDateTime.now().toString());
        tx.setTransactionDetails(details);
        Transaction saved = transactionRepository.save(tx);

        invalidate(SVC + "::transaction::" + id);
        invalidatePattern(SVC + "::S5-F10::*");
        invalidatePattern(SVC + "::S5-F11::*");

        // log REFUNDED event
        Map<String, Object> refundPayload = txPayload(saved, "REFUNDED");
        refundPayload.put("refundStrategy",    result.getStrategyName());
        refundPayload.put("refundReason",      request.getReason());
        refundPayload.put("refundAmount",      result.getRefundAmount());
        refundPayload.put("refundedItemIds",   result.getRefundedItemIds());
        notifyObservers("REFUNDED", refundPayload);

        return saved;
    }





}
