package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物品类型与价格规则组合DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsTypeWithPricingDTO {
    // 物品类型信息
    private Integer id;
    private String code;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Integer isActive;
    private LocalDateTime createTime;
    
    // 价格规则信息
    private Long pricingRuleId;
    private BigDecimal baseFee;
    private BigDecimal pricePerKg;
    private Integer pricingIsActive;
    private LocalDateTime pricingUpdateTime;
    
    /**
     * 是否有价格规则
     */
    public boolean hasPricingRule() {
        return pricingRuleId != null;
    }
}
