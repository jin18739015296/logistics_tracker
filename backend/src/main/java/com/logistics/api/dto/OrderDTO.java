package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long courierId;
    
    private String senderName;
    private String senderPhone;
    private String senderProvince;
    private String senderCity;
    private String senderDistrict;
    private String senderDetailAddress;
    private BigDecimal senderLatitude;
    private BigDecimal senderLongitude;
    
    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;
    private BigDecimal receiverLatitude;
    private BigDecimal receiverLongitude;
    
    private Integer goodsTypeId;
    private String goodsTypeName;
    private String goodsDescription;
    private BigDecimal goodsWeight;
    
    private String status;
    private String statusDesc;
    private String orderType;
    private BigDecimal totalAmount;
    private BigDecimal actualAmount;
    private String payType;
    
    private String dispatchType;
    private LocalDateTime dispatchTime;
    
    private String cancelReason;
    private LocalDateTime cancelTime;
    
    private String remark;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    private String courierName;
    private String courierPhone;

    private Double distance;
    private String senderAddress;
    private String receiverAddress;

    /** is_exception=1 或 status=exception 时用于管理端展示 */
    private Boolean isException;
    private String exceptionType;

    /**
     * 异常是否允许解回配送状态机；false 表示不可恢复类（灭失/损毁等），仅宜关单+退款。
     * 由 exception_type 推导，未识别类型时默认 true。
     */
    private Boolean exceptionRecoverable;

    /**
     * 异常允许的恢复动作列表：reassign-改派 / continue-继续配送 / cancel-取消订单。
     * 由 exception_type 推导。
     */
    private Set<String> allowedActions;

    /**
     * 当前登录用户在订单中的视角：下单人/寄件为 sender；仅通过收件电话关联的为 receiver。
     */
    private String viewerRole;

    /** 当前用户是否为订单收件电话对应账号（含本人寄本人） */
    private Boolean receiverPhoneMatch;

    /** 是否展示「确认收货」（仅已送达且收件人） */
    private Boolean canConfirmReceipt;

    /** 是否展示「评价」（已送达/已完成且收件人） */
    private Boolean canReviewOrder;
}
