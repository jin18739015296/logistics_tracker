package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long courierId;
    
    private Integer goodsTypeId;
    private String goodsDescription;
    private BigDecimal goodsWeight;
    
    private String status;
    private String orderType;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private String payType;
    
    private String dispatchType;
    private LocalDateTime dispatchTime;
    private Integer isException;
    private String exceptionType;
    
    private String cancelReason;
    private LocalDateTime cancelTime;
    
    private String remark;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
