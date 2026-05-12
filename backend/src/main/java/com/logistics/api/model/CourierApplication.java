package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierApplication {
    private Long id;
    private Long userId;
    
    // 审核状态: pending-待审核, approved-已通过, rejected-已拒绝
    private String status;
    
    // 身份认证信息
    private String idCardNo;
    private String idCardFront;
    private String idCardBack;
    private String idCardHold;
    
    // 车辆信息
    private String vehicleType;
    private String vehiclePlate;
    private String vehiclePhoto;
    
    // 驾驶证信息
    private String driverLicenseNo;
    private String driverLicensePhoto;
    
    // 行驶证信息
    private String vehicleLicenseNo;
    private String vehicleLicensePhoto;
    
    // 紧急联系人信息
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;
    
    // 工作相关
    private String workCity;
    private String workDistrict;
    
    // 审核信息
    private LocalDateTime reviewTime;
    private Long reviewerId;
    private String rejectReason;
    
    // 备注
    private String remark;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 关联的用户信息（查询时填充）
    private String realName;
    private String phone;
    private String username;
}
