package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierTaskDTO {
    private Long id;
    private String orderId;
    private String orderNo;
    private String status;
    /** 与 {@link com.logistics.api.enums.OrderStatus} 一致，供前端直接展示 */
    private String statusDesc;
    private String orderType;

    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderAddress;
    private BigDecimal senderLatitude;
    private BigDecimal senderLongitude;

    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverAddress;
    private BigDecimal receiverLatitude;
    private BigDecimal receiverLongitude;

    private String goodsDescription;
    private BigDecimal goodsWeight;
    private BigDecimal deliveryFee;
    private BigDecimal totalAmount;

    private LocalDateTime createTime;
    private LocalDateTime acceptTime;
    private LocalDateTime pickupTime;
    private LocalDateTime deliveryTime;
}
