package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingRule {
    private Long id;
    private Integer goodsTypeId;  // 物品类型ID
    private BigDecimal baseFee;   // 基础费用(元)
    private BigDecimal pricePerKg; // 每公斤价格(元)
    private Integer isActive;     // 是否启用: 1启用 0禁用
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
