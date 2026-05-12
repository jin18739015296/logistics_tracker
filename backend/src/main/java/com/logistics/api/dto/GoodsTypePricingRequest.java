package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 物品类型与价格规则创建/更新请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsTypePricingRequest {
    // 物品类型信息
    private Integer id;  // 编辑时传入
    private String code;
    private String name;
    private String description;
    private String icon;
    private Integer sortOrder;
    private Integer isActive;
    
    // 价格规则信息
    private BigDecimal baseFee;
    private BigDecimal pricePerKg;
    private Integer pricingIsActive;
}
