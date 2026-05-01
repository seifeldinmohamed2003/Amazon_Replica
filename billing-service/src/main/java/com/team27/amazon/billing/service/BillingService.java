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
import com.team27.amazon.billing.model.DiscountType;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionMethod;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.model.TransactionVoucher;
import com.team27.amazon.billing.model.Voucher;
import com.team27.amazon.billing.repository.TransactionRepository;
import com.team27.amazon.billing.repository.TransactionVoucherRepository;
import com.team27.amazon.billing.repository.VoucherRepository;
import com.team27.amazon.common.events.AbstractEventSubject;
import com.team27.amazon.common.events.MongoEventLogger;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;

@Service
public class BillingService extends AbstractEventSubject {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private TransactionVoucherRepository transactionVoucherRepository;

    @Autowired
    @Qualifier("billingEventLogger")
    private MongoEventLogger mongoEventLogger;

    @Autowired
    private ObjectArrayDtoAdapter objectArrayDtoAdapter;

    @PostConstruct
    public void initObserver() {
        register(mongoEventLogger);
    }

    // ── existing ─────────────────────────────────────────────────────────────

    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.searchTransactions(status, startDate, endDate);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));
    }
    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voucher not found"));
    }

    public Voucher createVoucher(Voucher voucher) {
        Voucher savedVoucher = voucherRepository.save(voucher);
        notifyObservers("VOUCHER_CREATED", billingEventPayload(null, null, null, Map.of(
                "voucherId", savedVoucher.getId(),
                "code", savedVoucher.getCode(),
                "details", voucherDetails(savedVoucher)
        )));
        return savedVoucher;
    }

    public Voucher updateVoucher(Long id, Voucher voucher) {
        voucher.setId(id);
        Voucher savedVoucher = voucherRepository.save(voucher);
        notifyObservers("VOUCHER_UPDATED", billingEventPayload(null, null, null, Map.of(
                "voucherId", savedVoucher.getId(),
                "code", savedVoucher.getCode(),
                "details", voucherDetails(savedVoucher)
        )));
        return savedVoucher;
    }

    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
        notifyObservers("VOUCHER_DELETED", billingEventPayload(null, null, null, Map.of("voucherId", id)));
    }

    public Transaction updateTransaction(Long id, Transaction updated) {
        Transaction t = getTransactionById(id);
        if (updated.getAmount() != null) t.setAmount(updated.getAmount());
        if (updated.getMethod() != null) t.setMethod(updated.getMethod());
        if (updated.getStatus() != null) t.setStatus(updated.getStatus());
        if (updated.getTransactionDetails() != null) t.setTransactionDetails(updated.getTransactionDetails());
        if (updated.getOrderId() != null) t.setOrderId(updated.getOrderId());
        if (updated.getUserId() != null) t.setUserId(updated.getUserId());
        Transaction savedTransaction = transactionRepository.save(t);
        notifyObservers("TRANSACTION_UPDATED", billingEventPayload(savedTransaction.getId(), savedTransaction.getMethod() == null ? null : savedTransaction.getMethod().name(), savedTransaction.getAmount(), Map.of(
            "status", savedTransaction.getStatus() == null ? null : savedTransaction.getStatus().name(),
            "details", transactionDetails(savedTransaction)
        )));
        return savedTransaction;
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
        notifyObservers("TRANSACTION_DELETED", billingEventPayload(id, null, null, Map.of()));
    }

    public TransactionVoucher createTransactionVoucher(TransactionVoucher tv) {
        if (tv.getAppliedAt() == null) tv.setAppliedAt(java.time.LocalDateTime.now());
        TransactionVoucher saved = transactionVoucherRepository.save(tv);
        notifyObservers("TRANSACTION_VOUCHER_CREATED", billingEventPayload(
            saved.getTransaction() == null ? null : saved.getTransaction().getId(),
            null,
            null,
            Map.of(
                "voucherId", saved.getVoucher() == null ? null : saved.getVoucher().getId(),
                "discountApplied", saved.getDiscountApplied()
            )
        ));
        return saved;
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
        notifyObservers("TRANSACTION_VOUCHER_DELETED", billingEventPayload(null, null, null, Map.of("transactionVoucherId", id)));
    }

    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(LocalDateTime.now());
        }
        Transaction savedTransaction = transactionRepository.save(transaction);
        String action = savedTransaction.getStatus() == null || savedTransaction.getStatus().name().equals("PENDING")
                ? "CREATED"
                : savedTransaction.getStatus().name();
        notifyObservers(action, billingEventPayload(savedTransaction.getId(), savedTransaction.getMethod() == null ? null : savedTransaction.getMethod().name(), savedTransaction.getAmount(), Map.of(
                "status", savedTransaction.getStatus() == null ? null : savedTransaction.getStatus().name(),
                "details", transactionDetails(savedTransaction)
        )));
        return savedTransaction;
    }

    @Transactional
    public Transaction processRefund(Long id, String reason) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already refunded");
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only COMPLETED transactions allowed");
        }

        transaction.setStatus(TransactionStatus.REFUNDED);

        Map<String, Object> details = transaction.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("refundReason", reason);
        details.put("refundedAt", LocalDateTime.now().toString());
        transaction.setTransactionDetails(details);

        Transaction savedTransaction = transactionRepository.save(transaction);
        notifyObservers("REFUNDED", billingEventPayload(savedTransaction.getId(), savedTransaction.getMethod() == null ? null : savedTransaction.getMethod().name(), savedTransaction.getAmount(), Map.of(
            "reason", reason,
            "status", savedTransaction.getStatus().name(),
            "details", transactionDetails(savedTransaction)
        )));
        return savedTransaction;
    }

    public UserTransactionSummaryDTO getUserTransactionSummary(Long userId) {
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

        return UserTransactionSummaryDTO.builder()
                .userId(userId)
                .totalTransactions(totalTransactions)
                .totalAmount(totalAmount)
                .methodBreakdown(methodBreakdown)
                .build();
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



    @Transactional
    public Transaction applyVoucherToTransaction(Long transactionId, Long voucherId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (transaction.getStatus() == TransactionStatus.COMPLETED ||
                transaction.getStatus() == TransactionStatus.REFUNDED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "cannot apply voucher to a completed/cancelled transaction");
        }

        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher not found"));

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

        notifyObservers("VOUCHER_APPLIED", billingEventPayload(transactionId, transaction.getMethod() == null ? null : transaction.getMethod().name(), transaction.getAmount(), Map.of(
            "voucherId", voucherId,
            "discount", discount,
            "details", transactionDetails(transaction)
        )));

        return transactionRepository.findById(transactionId).get();
    }

    // ── S5-F6 ── Revenue Report by Date Range ────────────────────────────────

    public RevenueReportDTO getRevenueReport(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must not be after endDate");
        }

        Double totalRevenue = transactionRepository.sumCompletedRevenue(startDate, endDate);
        Long totalTx = transactionRepository.countCompleted(startDate, endDate);
        Double refundedAmount = transactionRepository.sumRefundedAmount(startDate, endDate);
        Long refundCount = transactionRepository.countRefunded(startDate, endDate);

        // null-safe defaults
        if (totalRevenue == null) totalRevenue = 0.0;
        if (totalTx == null) totalTx = 0L;
        if (refundedAmount == null) refundedAmount = 0.0;
        if (refundCount == null) refundCount = 0L;


        double average = totalTx > 0 ? totalRevenue / totalTx : 0.0;

        return RevenueReportDTO.builder()
                .totalRevenue(totalRevenue)
                .totalTransactions(totalTx)
                .averageTransaction(average)
                .refundedAmount(refundedAmount)
                .refundCount(refundCount)
                .build();
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

        Transaction savedTransaction = transactionRepository.save(tx);
        notifyObservers("RETRY_ATTEMPTED", billingEventPayload(savedTransaction.getId(), savedTransaction.getMethod() == null ? null : savedTransaction.getMethod().name(), savedTransaction.getAmount(), Map.of(
            "retryAttempt", currentRetry + 1,
            "status", savedTransaction.getStatus().name(),
            "details", transactionDetails(savedTransaction)
        )));
        return savedTransaction;
    }

    // ── S5-F8 ── Get Transaction Details with Applied Vouchers ───────────────

    public TransactionDetailsDTO getTransactionDetails(Long transactionId) {
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

        return TransactionDetailsDTO.builder()
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
    }

    // ── S5-F9 ── Get Most Used Vouchers Report ───────────────────────────────

    public List<VoucherUsageDTO> getTopUsedVouchers(int limit) {
        List<Object[]> rows = transactionVoucherRepository.findTopUsedVouchers();

        List<VoucherUsageDTO> result = new ArrayList<>();
        int count = 0;

        for (Object[] row : rows) {
            if (count >= limit) break;

           result.add(objectArrayDtoAdapter.toVoucherUsageDTO(row));
     count++;}

        return result;
    }

    private Map<String, Object> billingEventPayload(Long transactionId,
                                                    String method,
                                                    Double amount,
                                                    Map<String, Object> details) {
        Map<String, Object> payload = new HashMap<>();
        if (transactionId != null) {
            payload.put("transactionId", transactionId);
        }
        if (method != null) {
            payload.put("method", method);
        }
        if (amount != null) {
            payload.put("amount", amount);
        }
        payload.put("details", details == null ? new HashMap<>() : new HashMap<>(details));
        return payload;
    }

    private Map<String, Object> transactionDetails(Transaction transaction) {
        Map<String, Object> details = new HashMap<>();
        if (transaction == null) {
            return details;
        }

        details.put("orderId", transaction.getOrderId());
        details.put("userId", transaction.getUserId());
        details.put("status", transaction.getStatus() == null ? null : transaction.getStatus().name());
        details.put("transactionDetails", transaction.getTransactionDetails() == null ? new HashMap<>() : new HashMap<>(transaction.getTransactionDetails()));
        return details;
    }

    private Map<String, Object> voucherDetails(Voucher voucher) {
        Map<String, Object> details = new HashMap<>();
        if (voucher == null) {
            return details;
        }

        details.put("code", voucher.getCode());
        details.put("discountType", voucher.getDiscountType() == null ? null : voucher.getDiscountType().name());
        details.put("discountValue", voucher.getDiscountValue());
        details.put("active", voucher.getActive());
        return details;
    }
}
