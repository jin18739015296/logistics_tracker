package com.logistics.api.service;

import com.logistics.api.model.Wallet;
import com.logistics.api.model.WalletTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {

    Wallet getOrCreateWallet(Long userId);

    BigDecimal getBalance(Long userId);

    void addIncome(Long userId, BigDecimal amount, Long orderId);

    void deductPayment(Long userId, BigDecimal amount, Long orderId, String orderNo);

    void refund(Long userId, BigDecimal amount, Long orderId, String orderNo, String reason);

    List<WalletTransaction> getTransactions(Long userId);

    List<WalletTransaction> getTransactions(Long userId, String type);

    List<WalletTransaction> getTransactionsWithPagination(Long userId, String type, int page, int size);

    List<WalletTransaction> getIncomeRecords(Long userId);

    java.math.BigDecimal getRefundedAmount(Long orderId);

    void recharge(Long userId, BigDecimal amount);
}
