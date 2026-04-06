package com.team27.amazon.billing.service;
import com.team27.amazon.billing.dto.UserTransactionSummaryDTO;
import com.team27.amazon.billing.model.TransactionMethod;
import com.team27.amazon.billing.model.TransactionVoucher;
import com.team27.amazon.billing.repository.TransactionVoucherRepository;
import com.team27.amazon.billing.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.team27.amazon.billing.model.Transaction;
import com.team27.amazon.billing.model.TransactionStatus;
import com.team27.amazon.billing.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import com.team27.amazon.billing.model.DiscountType;
import com.team27.amazon.billing.model.Voucher;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;




@Service
public class BillingService {

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private TransactionVoucherRepository transactionVoucherRepository;

    public List<Transaction> searchTransactions(String status, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.searchTransactions(status, startDate, endDate);
    }

    public Transaction saveTransaction(Transaction transaction) {
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

        if (details == null) {
            details = new HashMap<>();
        }

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must be DELIVERED to process payment");
        }

        int completedCount = transactionRepository.countCompletedTransactionsByOrderId(orderId);
        if (completedCount > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
        }

        Transaction transaction = transactionRepository.findPendingTransactionByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No pending transaction found for this order"));

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setMethod(TransactionMethod.valueOf(method));

        Map<String, Object> details = transaction.getTransactionDetails();
        if (details == null) details = new HashMap<>();
        details.put("gatewayResponse", "approved");
        if (cardLastFour != null) details.put("cardLastFour", cardLastFour);
        transaction.setTransactionDetails(details);

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


}