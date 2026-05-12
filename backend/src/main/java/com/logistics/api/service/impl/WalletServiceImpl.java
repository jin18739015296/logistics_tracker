package com.logistics.api.service.impl;

import com.logistics.api.mapper.WalletMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.WalletTransactionMapper;
import com.logistics.api.model.Wallet;
import com.logistics.api.model.Order;
import com.logistics.api.model.WalletTransaction;
import com.logistics.api.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletTransactionMapper walletTransactionMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public Wallet getOrCreateWallet(Long userId) {
        Wallet wallet = walletMapper.selectByUserId(userId);
        if (wallet == null) {
            wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setFrozenAmount(BigDecimal.ZERO);
            wallet.setTotalIncome(BigDecimal.ZERO);
            wallet.setTotalWithdraw(BigDecimal.ZERO);
            walletMapper.insertIgnore(wallet);
            log.info("创建用户钱包: userId={}", userId);
            wallet = walletMapper.selectByUserId(userId);
        }
        return wallet;
    }

    @Override
    public BigDecimal getBalance(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return wallet.getBalance();
    }

    @Override
    @Transactional
    public void addIncome(Long userId, BigDecimal amount, Long orderId) {
        Wallet wallet = getOrCreateWallet(userId);

        walletMapper.addIncome(userId, amount);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setType("income");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance().add(amount));
        transaction.setDescription("配送收入");
        walletTransactionMapper.insert(transaction);

        log.info("添加收入: userId={}, amount={}, orderId={}", userId, amount, orderId);
    }

    @Override
    @Transactional
    public void deductPayment(Long userId, BigDecimal amount, Long orderId, String orderNo) {
        Wallet wallet = getOrCreateWallet(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new com.logistics.api.common.BusinessException(
                    com.logistics.api.common.ErrorCode.BALANCE_NOT_ENOUGH, "钱包余额不足");
        }

        walletMapper.updateBalance(userId, amount.negate());

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setOrderNo(orderNo);
        transaction.setType("payment");
        transaction.setAmount(amount.negate());
        transaction.setBalanceAfter(wallet.getBalance().subtract(amount));
        transaction.setDescription("订单支付");
        walletTransactionMapper.insert(transaction);

        log.info("钱包扣款: userId={}, amount={}, orderId={}", userId, amount, orderId);
    }

    @Override
    @Transactional
    public void refund(Long userId, BigDecimal amount, Long orderId, String orderNo, String reason) {
        Wallet wallet = getOrCreateWallet(userId);

        walletMapper.updateBalance(userId, amount);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setOrderId(orderId);
        transaction.setOrderNo(orderNo);
        transaction.setType("refund");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance().add(amount));
        transaction.setDescription("订单退款" + (reason != null && !reason.isBlank() ? "：" + reason : ""));
        walletTransactionMapper.insert(transaction);

        log.info("钱包退款: userId={}, amount={}, orderId={}", userId, amount, orderId);
    }

    @Override
    public List<WalletTransaction> getTransactions(Long userId) {
        List<WalletTransaction> list = walletTransactionMapper.selectByUserId(userId);
        enrichOrderNos(list);
        return list;
    }

    @Override
    public List<WalletTransaction> getTransactions(Long userId, String type) {
        List<WalletTransaction> list;
        if (type == null || type.isEmpty()) {
            list = walletTransactionMapper.selectByUserId(userId);
        } else {
            list = walletTransactionMapper.selectByUserIdAndType(userId, type);
        }
        enrichOrderNos(list);
        return list;
    }

    @Override
    public List<WalletTransaction> getTransactionsWithPagination(Long userId, String type, int page, int size) {
        int offset = (page - 1) * size;
        List<WalletTransaction> list;
        if (type == null || type.isEmpty()) {
            list = walletTransactionMapper.selectByUserIdWithPagination(userId, size, offset);
        } else {
            list = walletTransactionMapper.selectByUserIdAndTypeWithPagination(userId, type, size, offset);
        }
        enrichOrderNos(list);
        return list;
    }

    @Override
    public List<WalletTransaction> getIncomeRecords(Long userId) {
        List<WalletTransaction> list = walletTransactionMapper.selectByUserIdAndType(userId, "income");
        enrichOrderNos(list);
        return list;
    }

    @Override
    public BigDecimal getRefundedAmount(Long orderId) {
        if (orderId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal refunded = walletTransactionMapper.sumRefundAmountByOrderId(orderId);
        return refunded != null ? refunded : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public void recharge(Long userId, BigDecimal amount) {
        Wallet wallet = getOrCreateWallet(userId);

        walletMapper.updateBalance(userId, amount);

        WalletTransaction transaction = new WalletTransaction();
        transaction.setUserId(userId);
        transaction.setType("recharge");
        transaction.setAmount(amount);
        transaction.setBalanceAfter(wallet.getBalance().add(amount));
        transaction.setDescription("钱包充值");
        walletTransactionMapper.insert(transaction);

        log.info("钱包充值: userId={}, amount={}", userId, amount);
    }

    private void enrichOrderNos(List<WalletTransaction> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(WalletTransaction::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        List<Order> orders = orderMapper.selectByIds(ids);
        Map<Long, String> map = orders.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        for (WalletTransaction t : list) {
            if (t.getOrderId() != null) {
                t.setOrderNo(map.get(t.getOrderId()));
            }
        }
    }
}
