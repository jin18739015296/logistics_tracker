package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistancePricingRule {
    private Long id;
    
    private BigDecimal distanceMin;
    private BigDecimal distanceMax;
    
    private BigDecimal baseFee;
    private BigDecimal pricePerKm;
    
    private Integer isActive;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
