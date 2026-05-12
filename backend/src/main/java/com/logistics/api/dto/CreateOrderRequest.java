package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建订单请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    // 寄件人信息
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderDetailAddress;
    private BigDecimal senderLatitude;
    private BigDecimal senderLongitude;
    
    // 收件人信息
    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;
    private BigDecimal receiverLatitude;
    private BigDecimal receiverLongitude;
    
    // 物品信息
    private Integer goodsTypeId;
    private String goodsDescription;
    private BigDecimal goodsWeight;
    private String orderType;
    private String remark;
}
