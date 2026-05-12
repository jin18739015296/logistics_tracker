package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {
    private Long id;
    private Long userId;
    /** 关联订单主键（API 返回；界面展示请用 orderNo） */
    private Long orderId;
    private String orderNo;
    private String type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String description;
    private LocalDateTime createTime;
}
