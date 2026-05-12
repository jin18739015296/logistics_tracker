package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.Wallet;
import com.logistics.api.model.WalletTransaction;
import com.logistics.api.service.WalletService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/courier/wallet")
public class CourierWalletController {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<Result<Map<String, Object>>> getWallet(Principal principal) {
        Long courierId = userService.getUserByUsername(principal.getName()).getId();
        Wallet wallet = walletService.getOrCreateWallet(courierId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("balance", wallet.getBalance());
        result.put("frozenAmount", wallet.getFrozenAmount());
        result.put("totalIncome", wallet.getTotalIncome());
        result.put("totalWithdraw", wallet.getTotalWithdraw());
        result.put("availableBalance", wallet.getBalance().subtract(wallet.getFrozenAmount()));
        
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/transactions")
    public ResponseEntity<Result<List<WalletTransaction>>> getTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        Long courierId = userService.getUserByUsername(principal.getName()).getId();
        List<WalletTransaction> transactions = walletService.getTransactionsWithPagination(courierId, type, page, size);
        return ResponseEntity.ok(Result.success(transactions));
    }

    @GetMapping("/income-records")
    public ResponseEntity<Result<List<WalletTransaction>>> getIncomeRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        Long courierId = userService.getUserByUsername(principal.getName()).getId();
        List<WalletTransaction> records = walletService.getTransactionsWithPagination(courierId, "income", page, size);
        return ResponseEntity.ok(Result.success(records));
    }
}
