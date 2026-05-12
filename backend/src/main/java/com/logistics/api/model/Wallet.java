package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozenAmount;
    private BigDecimal totalIncome;
    private BigDecimal totalWithdraw;
    private LocalDateTime updateTime;
}
