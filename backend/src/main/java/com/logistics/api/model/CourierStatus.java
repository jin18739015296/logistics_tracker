package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送员实时状态
 * 用于记录配送员的当前工作状态和位置
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierStatus {
    private Long id;
    private Long courierId;

    /**
     * 工作状态：
     * - offline: 离线/下班
     * - idle: 空闲，可接单
     * - busy: 配送中
     */
    private String status;

    /**
     * 当前位置（经纬度）
     */
    private BigDecimal currentLat;
    private BigDecimal currentLng;

    /**
     * 当前订单数
     */
    private Integer currentOrderCount;

    private LocalDateTime updateTime;
}
