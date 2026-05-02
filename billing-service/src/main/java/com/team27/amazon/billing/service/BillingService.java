package com.team27.amazon.billing.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.team27.amazon.billing.adapter.ObjectArrayDtoAdapter;
import com.team27.amazon.billing.dto.RevenueReportDTO;
import com.team27.amazon.billing.dto.TransactionDetailsDTO;
import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import com.team27.amazon.billing.dto.VoucherUsageDTO;
import com.team27.amazon.billing.model.AuditLogDocument;
import com.team27.amazon.billing.model.DiscountType;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionMethod;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.model.TransactionVoucher;
import com.team27.amazon.billing.model.Voucher;
import com.team27.amazon.billing.repository.TransactionAuditRepository;
import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.billing.repository.TransactionVoucherRepository;
import com.team27.amazon.billing.repository.VoucherRepository;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

@Service
public class BillingService {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private TransactionVoucherRepository transactionVoucherRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TransactionAuditRepository transactionAuditRepository; 
    @Autowired
    private MongoEventLogger mongoEventLogger; 
    @Autowired
    private MongoDocumentAdapter mongoDocumentAdapter;

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    @Autowired
    private TransactionAuditEventRepository auditRepository;

    @Autowired
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    private void invalidateVoucherCaches(Long voucherId) {
        cacheService.delete(vcKey(voucherId));
        cacheService.deleteByPattern(SVC + "::S5-F9::*");
    }

    private void writeAuditEvent(Long transactionId, String action, String method,
                                 Double amount, Map<String, Object> details) {
        try {
            TransactionAuditEvent event = new TransactionAuditEvent(
                    transactionId, action, LocalDateTime.now(), method, amount, details);
            auditRepository.save(event);
        } catch (Exception e) {
            log.warn("MongoDB audit write failed for transactionId {}: {}", transactionId, e.getMessage());
        }
    }

    private List<Map<String, Object>> buildRefundedItemsList(List<Long> itemIds, Long orderId) {
        List<Map<String, Object>> refundedItems = new ArrayList<>();
        if (itemIds == null || itemIds.isEmpty()) return refundedItems;

        List<Object[]> rows = transactionRepository.findItemDetailsByIds(itemIds);
        for (Object[] row : rows) {
            Long itemId = ((Number) row[0]).longValue();
            Integer quantity = ((Number) row[1]).intValue();
            Double price = ((Number) row[2]).doubleValue();

            Map<String, Object> item = new HashMap<>();
            item.put("orderItemId", itemId);
            item.put("quantity", quantity);
            item.put("amount", price * quantity);
            refundedItems.add(item);
        }
        return refundedItems;
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction getTransactionById(Long id) {
        String key = txKey(id);
        Transaction cached = cacheService.get(key, Transaction.class);
        if (cached != null) return cached;

        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));
        cacheService.set(key, t, 15);
        return t;
    }

    public Voucher getVoucherById(Long id) {
        String key = vcKey(id);
        Voucher cached = cacheService.get(key, Voucher.class);
        if (cached != null) return cached;

        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Voucher not found"));
        cacheService.set(key, v, 15);
        return v;
    }

    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    public TransactionVoucher getTransactionVoucherById(Long id) {
        String key = tvKey(id);
        TransactionVoucher cached = cacheService.get(key, TransactionVoucher.class);
        if (cached != null) return cached;

        TransactionVoucher tv = transactionVoucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("TransactionVoucher not found"));
        cacheService.set(key, tv, 15);
        return tv;
    }

    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(LocalDateTime.now());
        }
        Transaction saved = transactionRepository.save(transaction);
        invalidateTransactionCaches(saved.getId());
        return saved;
    }

    public Transaction updateTransaction(Long id, Transaction updated) {
        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));
        if (updated.getAmount() != null) t.setAmount(updated.getAmount());
        if (updated.getMethod() != null) t.setMethod(updated.getMethod());
        if (updated.getStatus() != null) t.setStatus(updated.getStatus());
        if (updated.getTransactionDetails() != null) t.setTransactionDetails(updated.getTransactionDetails());
        if (updated.getOrderId() != null) t.setOrderId(updated.getOrderId());
        if (updated.getUserId() != null) t.setUserId(updated.getUserId());
        Transaction saved = transactionRepository.save(t);
        invalidateTransactionCaches(saved.getId());
        return saved;
    }

    public void deleteTransaction(Long id) {
        invalidateTransactionCaches(id);
        transactionRepository.deleteById(id);
    }

    public Voucher saveVoucher(Voucher voucher) {
        Voucher saved = voucherRepository.save(voucher);
        invalidateVoucherCaches(saved.getId());
        return saved;
    }

    public Voucher updateVoucher(Long id, Voucher updated) {
        updated.setId(id);
        Voucher saved = voucherRepository.save(updated);
        invalidateVoucherCaches(saved.getId());
        return saved;
    }

    public void deleteVoucher(Long id) {
        invalidateVoucherCaches(id);
        voucherRepository.deleteById(id);
    }

    public TransactionVoucher createTransactionVoucher(TransactionVoucher tv) {
        if (tv.getAppliedAt() == null) tv.setAppliedAt(LocalDateTime.now());
        TransactionVoucher saved = transactionVoucherRepository.save(tv);
        cacheService.delete(tvKey(saved.getId()));
        return saved;
    }

    public List<TransactionVoucher> getAllTransactionVouchers() {
        return transactionVoucherRepository.findAll();
    }

    public void deleteTransactionVoucher(Long id) {
        cacheService.delete(tvKey(id));
        transactionVoucherRepository.deleteById(id);
    }

    // ── S5-F1 ── Search Transactions ─────────────────────────────────────────

    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        String key = f1Key(status, startDate, endDate);
        List<Transaction> cached = cacheService.get(key, new TypeReference<List<Transaction>>() {});
        if (cached != null) return cached;

        List<Transaction> result = transactionRepository.searchTransactions(status, startDate, endDate);
        cacheService.set(key, result, 5);
        return result;
    }

    // ── S5-F2 ── Process Refund ───────────────────────────────────────────────

    @Transactional
    public Transaction processRefund(Long id, String reason) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already refunded");
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED transactions allowed");
        }

        transaction.setStatus(TransactionStatus.REFUNDED);

        Map<String, Object> details = transaction.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        transaction.setTransactionDetails(details);

        Transaction saved = transactionRepository.save(transaction);
        invalidateTransactionCaches(saved.getId());
        return saved;
    }

    // ── S5-F3 ── User Transaction Summary ────────────────────────────────────

    public UserTransactionSummaryDTO getUserTransactionSummary(Long userId) {
        String key = f3Key(userId);
        UserTransactionSummaryDTO cached = cacheService.get(key, UserTransactionSummaryDTO.class);
        if (cached != null) return cached;

        int userExists = transactionRepository.countUserById(userId);
        if (userExists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        List<Object[]> rows = transactionRepository.getTransactionSummaryByUser(userId);

        Map<String, Double> methodBreakdown = new HashMap<>();
        long totalTransactions = 0;
        double totalAmount = 0.0;
        for (Object[] row : rows) {
            String method = objectArrayDtoAdapter.toTransactionMethod(row);
            long count = objectArrayDtoAdapter.toTransactionCount(row);
            double sum = objectArrayDtoAdapter.toTransactionSum(row);

            methodBreakdown.put(method, sum);
            totalTransactions += count;
            totalAmount += sum;
        }

        UserTransactionSummaryDTO dto =
                new UserTransactionSummaryDTO(userId, totalTransactions, totalAmount, methodBreakdown);
        cacheService.set(key, dto, 10);
        return dto;
    }

@Transactional
public Transaction processTransactionForOrder(Long orderId, String method, String cardLastFour, boolean simulateFailure) {
    String orderStatus = transactionRepository.findOrderStatusById(orderId);
    if (orderStatus == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    if (!orderStatus.equals("DELIVERED")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Order must be DELIVERED to process payment");
    }

    int completedCount = transactionRepository.countCompletedTransactionsByOrderId(orderId);
    if (completedCount > 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
    }

    Double amount = transactionRepository.findOrderTotalAmountById(orderId);
    Long userId = transactionRepository.findUserIdByOrderId(orderId);

    Map<String, Object> details = new HashMap<>();
    details.put("gatewayResponse", simulateFailure ? "declined" : "approved");
    if (cardLastFour != null) {
        details.put("cardLastFour", cardLastFour);
    }

    if (simulateFailure) {
        details.put("failureReason", "simulated failure");
        details.put("failedAt", LocalDateTime.now().toString());
    }

    Transaction transaction = new Transaction();
    transaction.setOrderId(orderId);
    transaction.setUserId(userId);
    transaction.setAmount(amount != null ? amount : 0.0);
    transaction.setMethod(TransactionMethod.valueOf(method));
    transaction.setStatus(simulateFailure ? TransactionStatus.FAILED : TransactionStatus.COMPLETED);
    transaction.setTransactionDetails(details);
    transaction.setCreatedAt(LocalDateTime.now());

    Transaction savedTransaction = transactionRepository.save(transaction);

    if (simulateFailure) {
        notifyObservers("FAILED", billingEventPayload(
                savedTransaction.getId(),
                savedTransaction.getMethod().name(),
                savedTransaction.getAmount(),
                Map.of(
                        "status", savedTransaction.getStatus().name(),
                        "details", transactionDetails(savedTransaction)
                )
        ));
    } else {
        notifyObservers("CREATED", billingEventPayload(
                savedTransaction.getId(),
                savedTransaction.getMethod().name(),
                savedTransaction.getAmount(),
                Map.of(
                        "status", "PENDING",
                        "details", transactionDetails(savedTransaction)
                )
        ));

        notifyObservers("COMPLETED", billingEventPayload(
                savedTransaction.getId(),
                savedTransaction.getMethod().name(),
                savedTransaction.getAmount(),
                Map.of(
                        "status", savedTransaction.getStatus().name(),
                        "details", transactionDetails(savedTransaction)
                )
        ));
    }

    return savedTransaction;
}


    // ── S5-F5 ── Apply Voucher to Transaction ─────────────────────────────────

    @Transactional
    public Transaction applyVoucherToTransaction(Long transactionId, Long voucherId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.COMPLETED ||
                transaction.getStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply voucher to a completed/cancelled transaction");
        }

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Voucher not found"));

        if (!voucher.getActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher is not active");
        }
        if (voucher.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher has expired");
        }
        if (voucher.getCurrentUses() >= voucher.getMaxUses()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voucher usage limit reached");
        }

        Optional<TransactionVoucher> duplicate = transactionVoucherRepository
                .findByTransactionIdAndVoucherId(transactionId, voucherId);
        if (duplicate.isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "voucher already applied");
        }

        double discount;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = transaction.getAmount() * voucher.getDiscountValue() / 100;
        } else {
            discount = voucher.getDiscountValue();
        }
        if (discount > transaction.getAmount()) {
            discount = transaction.getAmount();
        }

        TransactionVoucher tv = new TransactionVoucher();
        tv.setTransaction(transaction);
        tv.setVoucher(voucher);
        tv.setDiscountApplied(discount);
        tv.setAppliedAt(LocalDateTime.now());
        transactionVoucherRepository.save(tv);

        voucher.setCurrentUses(voucher.getCurrentUses() + 1);
        voucherRepository.save(voucher);

        invalidateTransactionCaches(transactionId);
        invalidateVoucherCaches(voucherId);

        return transactionRepository.findById(transactionId).get();
    }

    // ── S5-F6 ── Revenue Report ───────────────────────────────────────────────

    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }

        String key = f6Key(startDate, endDate);
        RevenueReportDTO cached = cacheService.get(key, RevenueReportDTO.class);
        if (cached != null) return cached;

        Double totalRevenue   = transactionRepository.sumCompletedRevenue(startDate, endDate);
        Long   totalTx        = transactionRepository.countCompleted(startDate, endDate);
        Double refundedAmount = transactionRepository.sumRefundedAmount(startDate, endDate);
        Long   refundCount    = transactionRepository.countRefunded(startDate, endDate);

        if (totalRevenue   == null) totalRevenue   = 0.0;
        if (totalTx        == null) totalTx        = 0L;
        if (refundedAmount == null) refundedAmount = 0.0;
        if (refundCount    == null) refundCount    = 0L;

        double average = totalTx > 0 ? totalRevenue / totalTx : 0.0;

        RevenueReportDTO dto =
                new RevenueReportDTO(totalRevenue, totalTx, average, refundedAmount, refundCount);
        cacheService.set(key, dto, 10);
        return dto;
    }

    // ── S5-F7 ── Retry Failed Transaction ────────────────────────────────────

    @Transactional
    public Transaction retryTransaction(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + id));

        if (tx.getStatus() != TransactionStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only FAILED transactions can be retried. Current status: " + tx.getStatus());
        }

        tx.setStatus(TransactionStatus.COMPLETED);

        Map<String, Object> details = tx.getTransactionDetails();
        if (details == null) details = new HashMap<>();

        int currentRetry = 0;
        if (details.get("retryAttempt") instanceof Number) {
            currentRetry = ((Number) details.get("retryAttempt")).intValue();
        }
        details.put("retryAttempt", currentRetry + 1);
        details.put("gatewayResponse", "approved");
        tx.setTransactionDetails(details);

        Transaction saved = transactionRepository.save(tx);
        invalidateTransactionCaches(saved.getId());
        return saved;
    }

    // ── S5-F8 ── Transaction Details with Vouchers ───────────────────────────

    public TransactionDetailsDTO getTransactionDetails(Long transactionId) {
        String key = f8Key(transactionId);
        TransactionDetailsDTO cached = cacheService.get(key, TransactionDetailsDTO.class);
        if (cached != null) return cached;

        Transaction tx = transactionRepository.findByIdWithVouchers(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transaction not found with id: " + transactionId));

        List<TransactionDetailsDTO.AppliedVoucherDTO> appliedVouchers = new ArrayList<>();
        double totalDiscount = 0.0;

        for (TransactionVoucher tv : tx.getTransactionVouchers()) {
            Voucher v = tv.getVoucher();
            appliedVouchers.add(new TransactionDetailsDTO.AppliedVoucherDTO(
                    v.getCode(),
                    v.getDiscountType().name(),
                    tv.getDiscountApplied(),
                    tv.getAppliedAt()
            ));
            totalDiscount += tv.getDiscountApplied();
        }

        TransactionDetailsDTO dto = new TransactionDetailsDTO();
        dto.setTransactionId(tx.getId());
        dto.setOrderId(tx.getOrderId());
        dto.setUserId(tx.getUserId());
        dto.setOriginalAmount(tx.getAmount());
        dto.setMethod(tx.getMethod().name());
        dto.setStatus(tx.getStatus().name());
        dto.setTransactionDetails(tx.getTransactionDetails());
        dto.setAppliedVouchers(appliedVouchers);
        dto.setTotalDiscount(totalDiscount);
        dto.setFinalAmount(tx.getAmount() - totalDiscount);

        cacheService.set(key, dto, 15);
        return dto;
    }

    // ── S5-F9 ── Top Used Vouchers ───────────────────────────────────────────

    public List<VoucherUsageDTO> getTopUsedVouchers(int limit) {
        String key = f9Key(limit);
        List<VoucherUsageDTO> cached =
                cacheService.get(key, new TypeReference<List<VoucherUsageDTO>>() {});
        if (cached != null) return cached;

        List<Object[]> rows = transactionVoucherRepository.findTopUsedVouchers();
        List<VoucherUsageDTO> result = new ArrayList<>();
        int count = 0;

        for (Object[] row : rows) {
            if (count >= limit) break;

           result.add(objectArrayDtoAdapter.toVoucherUsageDTO(row));
     count++;}

        cacheService.set(key, result, 10);
        return result;
    }

    //[S5-F10] Get Category Revenue with Return Impact

    public List<CategoryRevenueDTO> getCategoryRevenueReport() {
    String key = f10Key();
    List<CategoryRevenueDTO> cached = cacheService.get(key, new TypeReference<List<CategoryRevenueDTO>>() {});
    if (cached != null) return cached;
    List<Object[]> results = categoryRepository.getCategoryRevenueData();
    List<CategoryRevenueDTO> report = results.stream()
        .<CategoryRevenueDTO>map(row -> {
            String category = (row[0] != null) ? row[0].toString() : "Unknown";
            Double revenue = (row[1] != null) ? ((Number) row[1]).doubleValue() : 0.0;

            return CategoryRevenueDTO.builder()
                .categoryName(category)
                .netRevenue(revenue)
                .build();
        })
        .collect(Collectors.toList());

    mongoEventLogger.logEvent("REVENUE_REPORT_GENERATED", "System-Wide");
    cacheService.set(key, report, 10);
    return report;
}

    //[S5-F11] Get Transaction Lifecycle Audit
    public List<AuditLogDTO> getTransactionAuditTrail(String transactionId) {
    String key = f11Key(transactionId);
    List<AuditLogDTO> cached = cacheService.get(key, new TypeReference<List<AuditLogDTO>>() {});
    if (cached != null) return cached;
    List<AuditLogDocument> logs = transactionAuditRepository.findAllByTransactionId(transactionId);
    List<AuditLogDTO> dtos = logs.stream()
               .map(mongoDocumentAdapter::toDTO)
               .collect(Collectors.toList());

    cacheService.set(key, dtos, 15);
    return dtos;
}

    // ── S5-F12 ── Process Partial Item Refund ────────────────────────────────

    @Transactional
    public Transaction processPartialRefund(Long id, RefundRequest request) {

        // Step b — Find transaction
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        // Step c — Validate COMPLETED
        if (tx.getStatus() != TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Transaction must be COMPLETED to process a refund");
        }

        // Step d — Validate orderItemIds if refundAll=false
        if (!request.isRefundAll()) {
            if (request.getOrderItemIds() == null || request.getOrderItemIds().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "orderItemIds must not be empty when refundAll is false");
            }
            int validCount = transactionRepository.countItemsBelongingToOrder(
                    request.getOrderItemIds(), tx.getOrderId());
            if (validCount != request.getOrderItemIds().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Some orderItemIds do not belong to this transaction's order");
            }
        }

        // Step e — Select strategy (no if/else branching here — selector does it)
        RefundStrategy strategy = refundStrategySelector.select(tx, request);

        // Step f — Handle NoRefundStrategy
        if (strategy instanceof NoRefundStrategy) {
            Map<String, Object> denialDetails = new HashMap<>();
            denialDetails.put("refundStrategy", "NoRefundStrategy");
            denialDetails.put("reason", "return window expired");
            denialDetails.put("orderItemIds", request.getOrderItemIds());

            writeAuditEvent(tx.getId(), "REFUND_DENIED",
                    tx.getMethod().name(), tx.getAmount(), denialDetails);

            cacheService.deleteByPattern(SVC + "::S5-F10::*");
            cacheService.deleteByPattern(SVC + "::S5-F11::*");

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "return window expired");
        }

        // Step g — Calculate refund
        RefundResult result = strategy.calculateRefund(tx, request);
        tx.setStatus(TransactionStatus.REFUNDED);

        // Step h — Write additive JSONB keys
        Map<String, Object> details = tx.getTransactionDetails();
        if (details == null) details = new HashMap<>();

        List<Map<String, Object>> refundedItems =
                buildRefundedItemsList(result.getRefundedItemIds(), tx.getOrderId());

        details.put("refundAmount", result.getAmount());
        details.put("refundedItems", refundedItems);
        details.put("refundStrategy", strategy.getClass().getSimpleName());
        details.put("refundReason", request.getReason());
        details.put("refundedAt", LocalDateTime.now().toString());
        tx.setTransactionDetails(details);

        Transaction saved = transactionRepository.save(tx);

        // Step i — Log REFUNDED event to MongoDB
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("refundStrategy", strategy.getClass().getSimpleName());
        auditDetails.put("reason", request.getReason());
        auditDetails.put("originalAmount", tx.getAmount());
        auditDetails.put("refundAmount", result.getAmount());
        auditDetails.put("refundedItemIds", result.getRefundedItemIds());

        writeAuditEvent(tx.getId(), "REFUNDED",
                tx.getMethod().name(), result.getAmount(), auditDetails);

        // Step j — Invalidate caches
        invalidateTransactionCaches(id);
        cacheService.deleteByPattern(SVC + "::S5-F10::*");
        cacheService.deleteByPattern(SVC + "::S5-F11::*");

        return saved;
    }



}