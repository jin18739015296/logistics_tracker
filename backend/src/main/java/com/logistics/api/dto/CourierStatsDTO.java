package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatsDTO {
    private Integer totalDeliveries;
    private Integer monthDeliveries;
    private BigDecimal onTimeRate;
    private BigDecimal rating;
    private Integer reviewCount;
    private BigDecimal monthIncome;
    private BigDecimal totalIncome;
    private Integer monthWorkHours;
    private Integer level;
    private String levelName;
}
