package com.team27.amazon.billing.service;

import com.team27.amazon.billing.model.*;
import com.team27.amazon.billing.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class BillingService {

    private final TransactionRepository transactionRepository;
    private final VoucherRepository voucherRepository;
    private final TransactionVoucherRepository transactionVoucherRepository;

    public BillingService(TransactionRepository transactionRepository,
                          VoucherRepository voucherRepository,
                          TransactionVoucherRepository transactionVoucherRepository) {
        this.transactionRepository = transactionRepository;
        this.voucherRepository = voucherRepository;
        this.transactionVoucherRepository = transactionVoucherRepository;
    }


    public Transaction createTransaction(Transaction t) {
        if (t.getCreatedAt() == null) t.setCreatedAt(java.time.LocalDateTime.now());
        return transactionRepository.save(t);
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transaction updateTransaction(Long id, Transaction updated) {
        Transaction t = getTransactionById(id);
        t.setAmount(updated.getAmount());
        t.setMethod(updated.getMethod());
        t.setStatus(updated.getStatus());
        t.setTransactionDetails(updated.getTransactionDetails());
        return transactionRepository.save(t);
    }

    public void deleteTransaction(Long id) {
        transactionRepository.deleteById(id);
    }


    public Voucher createVoucher(Voucher v) {
        return voucherRepository.save(v);
    }

    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voucher not found"));
    }

    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    public Voucher updateVoucher(Long id, Voucher updated) {
        Voucher v = getVoucherById(id);
        v.setCode(updated.getCode());
        v.setDiscountType(updated.getDiscountType());
        v.setDiscountValue(updated.getDiscountValue());
        v.setMaxUses(updated.getMaxUses());
        v.setExpiryDate(updated.getExpiryDate());
        v.setActive(updated.getActive());
        return voucherRepository.save(v);
    }

    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
    }


    public TransactionVoucher createTransactionVoucher(TransactionVoucher tv) {
        tv.setAppliedAt(java.time.LocalDateTime.now());
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

}