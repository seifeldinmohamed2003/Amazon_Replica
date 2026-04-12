package com.team27.amazon.billing.service;

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
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class BillingService {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private TransactionVoucherRepository transactionVoucherRepository;

    // ── existing ─────────────────────────────────────────────────────────────

    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.searchTransactions(status, startDate, endDate);
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
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

    public Transaction updateTransaction(Long id, Transaction updated) {
        Transaction t = getTransactionById(id);
        if (updated.getAmount() != null) t.setAmount(updated.getAmount());
        if (updated.getMethod() != null) t.setMethod(updated.getMethod());
        if (updated.getStatus() != null) t.setStatus(updated.getStatus());
        if (updated.getTransactionDetails() != null) t.setTransactionDetails(updated.getTransactionDetails());
        if (updated.getOrderId() != null) t.setOrderId(updated.getOrderId());
        if (updated.getUserId() != null) t.setUserId(updated.getUserId());
        return transactionRepository.save(t);
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }

    public TransactionVoucher createTransactionVoucher(TransactionVoucher tv) {
        if (tv.getAppliedAt() == null) tv.setAppliedAt(java.time.LocalDateTime.now());
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

    public Transaction saveTransaction(Transaction transaction) {
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(LocalDateTime.now());
        }
        return transactionRepository.save(transaction);
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

        return transactionRepository.save(transaction);
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
            String method = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double sum = ((Number) row[2]).doubleValue();
            methodBreakdown.put(method, sum);
            totalTransactions += count;
            totalAmount += sum;
        }

        return new UserTransactionSummaryDTO(userId, totalTransactions, totalAmount, methodBreakdown);
    }

    @Transactional
    public Transaction processTransactionForOrder(Long orderId, String method, String cardLastFour) {
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
        details.put("gatewayResponse", "approved");
        if (cardLastFour != null) details.put("cardLastFour", cardLastFour);

        Transaction transaction = new Transaction();
        transaction.setOrderId(orderId);
        transaction.setUserId(userId);
        transaction.setAmount(amount != null ? amount : 0.0);
        transaction.setMethod(TransactionMethod.valueOf(method));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setTransactionDetails(details);
        transaction.setCreatedAt(LocalDateTime.now());

        return transactionRepository.save(transaction);
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

        return new RevenueReportDTO(totalRevenue, totalTx, average, refundedAmount, refundCount);
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

        return transactionRepository.save(tx);
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

        return dto;
    }

    // ── S5-F9 ── Get Most Used Vouchers Report ───────────────────────────────

    public List<VoucherUsageDTO> getTopUsedVouchers(int limit) {
        List<Object[]> rows = transactionVoucherRepository.findTopUsedVouchers();

        List<VoucherUsageDTO> result = new ArrayList<>();
        int count = 0;

        for (Object[] row : rows) {
            if (count >= limit) break;

            Voucher v             = (Voucher) row[0];
            Integer timesUsed     = ((Number) row[1]).intValue();
            Double  totalDiscount = ((Number) row[2]).doubleValue();
            boolean expired       = v.getExpiryDate() != null &&
                    v.getExpiryDate().isBefore(LocalDateTime.now());

            result.add(new VoucherUsageDTO(
                    v.getId(),
                    v.getCode(),
                    v.getDiscountType().name(),
                    v.getDiscountValue(),
                    timesUsed,
                    totalDiscount,
                    v.getActive(),
                    expired
            ));
            count++;
        }

        return result;
    }
}
