package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物流事件
 * 记录订单的关键物流节点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsEvent {
    private Long id;
    private Long orderId;
    private String status;
    private String description;
    private String location;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Long operatorId;
    private LocalDateTime createTime;
}
